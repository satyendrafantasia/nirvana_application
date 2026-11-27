package com.nirvana.application.controller;

import com.nirvana.application.model.dto.BookingRescheduleRequest;
import com.nirvana.application.model.enums.AdminActionType;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.AuditTrailService;
import com.nirvana.application.service.BookingService;
import com.nirvana.application.service.impl.TherapistRoster;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api/owner/spas/{spa}/desk")
// Front-desk staff may run the desk; endpoints marked OWNER below are for the owner (or manager) only.
@PreAuthorize("@staffAccess.desk(#spa)")
@RequiredArgsConstructor
public class OwnerDeskController {
    private static final String OWNER = "@staffAccess.spa(#spa)";
    private final com.nirvana.application.security.StaffAccess access;
    private final NamedParameterJdbcTemplate db;
    private final SpaRepository spas;
    private final BookingRepository bookings;
    private final BookingService bookingService;
    private final AuditTrailService audit;

    private final OwnerFinance finance;
    private final com.nirvana.application.service.SpaBookingReplies replies;
    private final OwnerCapacityReport capacity;
    private static final String JOINS = """
        FROM booking b JOIN app_user u ON u.id=b.user_id JOIN service s ON s.id=b.service_id
        LEFT JOIN therapist t ON t.id=b.therapist_id LEFT JOIN spa_room r ON r.id=b.room_id
        LEFT JOIN payment p ON p.booking_id=b.id
        LEFT JOIN (SELECT booking_id,SUM(amount_cents) AS paid,SUM(tip_cents) AS tips,COUNT(*) AS receipts,COUNT(returned_at) AS returned,
            MAX(collected_at) AS collected_at,MAX(method) AS method FROM spa_desk_receipt WHERE spa_id=:spa GROUP BY booking_id) d ON d.booking_id=b.id
        LEFT JOIN spa_booking_confirmation c ON c.booking_id=b.id AND c.spa_id=b.spa_id
        """;
    private static final String STATE = """
        CASE WHEN b.payment_type='PAID_BY_PACKAGE' THEN 'PACKAGE'
             WHEN d.receipts>0 AND d.returned=d.receipts THEN 'RETURNED_AT_SPA'
             WHEN d.receipts>0 AND (d.paid>=b.remainder_cents OR b.status NOT IN ('ARRIVED','CONFIRMED','COMPLETED')) THEN 'PAID_AT_SPA'
             WHEN p.payment_status IN ('CAPTURED','COMPLETED') THEN 'PAID'
             WHEN p.payment_status IN ('REFUNDED','PARTIALLY_REFUNDED') THEN p.payment_status
             WHEN b.remainder_cents<=COALESCE(d.paid,0) AND EXISTS (SELECT 1 FROM booking_wallet_spend w WHERE w.booking_id=b.id AND w.amount_cents>COALESCE(w.returned_cents,0)) THEN 'WALLET'
             WHEN b.gift_card_cents>0 AND b.remainder_cents<=COALESCE(d.paid,0) THEN 'GIFT_CARD'
             WHEN b.payment_mode='OFFLINE' AND b.status IN ('ARRIVED','CONFIRMED','COMPLETED') AND b.remainder_cents>COALESCE(d.paid,0) THEN 'DUE_AT_SPA'
             ELSE COALESCE(p.payment_status,'UNPAID') END
        """;
    /** What is still to take at the desk: the balance less any part payments. */
    private static final String OUTSTANDING = "(b.remainder_cents-COALESCE(d.paid,0))";
    /** The guest's name, or their phone number when they signed up without one. */
    private static final String CUSTOMER = "COALESCE(NULLIF(TRIM(u.name),''),u.phone)";
    public enum Period { TODAY, YESTERDAY, WEEK, MONTH, YEAR, CUSTOM }
    record Dates(LocalDate from, LocalDate to) {}
    private Dates range(Period period, LocalDate today, LocalDate from, LocalDate to) {
        return switch (period) {
            case TODAY -> new Dates(today,today);
            case YESTERDAY -> new Dates(today.minusDays(1),today.minusDays(1));
            case WEEK -> new Dates(today.minusDays(6),today);
            case MONTH -> new Dates(today.minusDays(29),today);
            case YEAR -> new Dates(today.plusDays(1).minusYears(1),today);
            case CUSTOM -> {
                if (from==null || to==null || to.isBefore(from) || ChronoUnit.DAYS.between(from,to)>365)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose an ordered range of up to 366 days");
                yield new Dates(from,to);
            }
        };
    }
    private Map<String,Object> params(long spa, ZoneId zone, Dates dates) {
        return new HashMap<>(Map.of("spa",spa,"start",utc(dates.from,zone),"end",utc(dates.to.plusDays(1),zone)));
    }
    private LocalDateTime utc(LocalDate date,ZoneId zone) {
        return date.atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
    private ZoneId zone(long spa) {
        return TherapistRoster.zone(spas.findById(spa).orElseThrow());
    }
    private Map<String,Object> counts(Map<String,Object> args) {
        return db.queryForMap("""
            SELECT COUNT(*) AS bookings, COUNT(CASE WHEN b.status='COMPLETED' THEN 1 END) AS completed,
              COUNT(CASE WHEN b.status='CONFIRMED' THEN 1 END) AS confirmed,
              COUNT(CASE WHEN b.status='CANCELLED' THEN 1 END) AS cancelled,
              COUNT(CASE WHEN c.status IN ('PENDING','NEEDS_CONTACT','NO_MOBILE') AND b.status='CONFIRMED' THEN 1 END) AS pending
            FROM booking b LEFT JOIN spa_booking_confirmation c ON c.booking_id=b.id
            WHERE b.spa_id=:spa AND b.start_ts>=:start AND b.start_ts<:end
            """,args);
    }
    @GetMapping("/summary")
    @Transactional(readOnly=true)
    public Map<String,Object> summary(@PathVariable long spa,@RequestParam(defaultValue="TODAY") Period period,
            @RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to) {
        ZoneId zone=zone(spa);
        LocalDate today=LocalDate.now(zone);
        Dates dates=range(period,today,from,to);
        var args=params(spa,zone,dates);
        long days=ChronoUnit.DAYS.between(dates.from,dates.to)+1;
        // Compare UTC instants against spa-local boundaries. This also works without MySQL timezone tables.
        var buckets=new ArrayList<String>();
        for(LocalDate start=dates.from; !start.isAfter(dates.to);) {
            LocalDate next=days>100?start.withDayOfMonth(1).plusMonths(1):days>31?start.plusWeeks(1):start.plusDays(1);
            if(next.isAfter(dates.to.plusDays(1))) next=dates.to.plusDays(1);
            int i=buckets.size();
            args.put("s"+i,utc(start,zone)); args.put("e"+i,utc(next,zone)); args.put("day"+i,start.toString());
            buckets.add("SELECT :day"+i+" AS day, :s"+i+" AS lo, :e"+i+" AS hi");
            start=next;
        }
        var trend=db.queryForList("SELECT days.day,COUNT(b.id) AS bookings,COUNT(CASE WHEN b.status='COMPLETED' THEN 1 END) AS completed, COUNT(CASE WHEN b.status='CONFIRMED' THEN 1 END) AS confirmed,COUNT(CASE WHEN b.status='CANCELLED' THEN 1 END) AS cancelled FROM ("+
            String.join(" UNION ALL ",buckets)+") days LEFT JOIN booking b ON b.spa_id=:spa AND b.start_ts>=days.lo AND b.start_ts<days.hi GROUP BY days.day ORDER BY days.day",args);
        var services=db.queryForList("""
            SELECT s.name,b.currency,SUM(b.price_cents+b.tax_cents-b.discount_cents) AS valueCents,COUNT(*) AS visits FROM booking b JOIN service s ON s.id=b.service_id
            WHERE b.spa_id=:spa AND b.status='COMPLETED' AND b.start_ts>=:start AND b.start_ts<:end
            GROUP BY s.id,s.name,b.currency ORDER BY visits DESC,s.name LIMIT 8
            """,args);
        var money=finance.totals(args);
        var due=db.queryForList("SELECT b.currency,SUM("+OUTSTANDING+") AS outstanding "+JOINS+
            " WHERE b.spa_id=:spa AND b.start_ts>=:start AND b.start_ts<:end AND ("+STATE+")='DUE_AT_SPA' GROUP BY b.currency",args);
        var result=new LinkedHashMap<String,Object>();
        var todayArgs=params(spa,zone,new Dates(today,today));
        // Still to arrive today: confirmed and not checked in, late guests included, until their appointment ends.
        result.put("arrivals",db.queryForObject("SELECT COUNT(*) FROM booking WHERE spa_id=:spa AND status='CONFIRMED' AND start_ts>=:start AND start_ts<:end AND end_ts>UTC_TIMESTAMP()",todayArgs,Long.class));
        // Each clash once: a pair of appointments sharing a therapist, or a room in different slots (guests in one
        // group slot share its room by design), plus each appointment that falls in a closure.
        result.put("conflicts",db.queryForObject("""
            SELECT (SELECT COUNT(*) FROM booking b JOIN booking o ON o.spa_id=b.spa_id AND o.id>b.id AND o.start_ts<b.end_ts AND o.end_ts>b.start_ts
                  AND o.status IN ('CONFIRMED','ARRIVED') AND (o.therapist_id=b.therapist_id OR (o.room_id=b.room_id AND o.slot_id<>b.slot_id))
                WHERE b.spa_id=:spa AND b.start_ts<:end AND b.end_ts>:start AND b.status IN ('CONFIRMED','ARRIVED'))
              +(SELECT COUNT(*) FROM booking b WHERE b.spa_id=:spa AND b.start_ts<:end AND b.end_ts>:start AND b.status IN ('CONFIRMED','ARRIVED')
                AND EXISTS(SELECT 1 FROM closure c WHERE c.spa_id=b.spa_id AND c.start_ts<b.end_ts AND c.end_ts>b.start_ts))
            """,todayArgs,Long.class));
        result.put("timezone",zone.getId()); result.put("today",today); result.put("dates",dates);
        result.put("counts",counts(args)); result.put("previous",counts(params(spa,zone,new Dates(dates.from.minusDays(days),dates.from.minusDays(1)))));
        result.put("cashTrend",finance.trend(args,String.join(" UNION ALL ",buckets))); result.put("packagesTrend",db.queryForList("SELECT days.day,(SELECT COUNT(*) FROM user_spa_package_subscription x WHERE x.spa_id=:spa AND x.payment_status='SUCCESS' AND x.purchase_date>=days.lo AND x.purchase_date<days.hi) AS sales,(SELECT COUNT(*) FROM user_spa_package_usage_log l WHERE l.spa_id=:spa AND l.usage_date>=days.lo AND l.usage_date<days.hi) AS redemptions FROM ("+String.join(" UNION ALL ",buckets)+") days ORDER BY days.day",args)); result.put("due",due); result.put("trend",trend); result.put("services",services); result.put("money",money);
        boolean owner=access.spa(spa);
        result.put("access",owner?"OWNER":"DESK");
        if(owner){
            Integer pct=db.queryForObject("SELECT commission_pct FROM spa WHERE id=:spa",args,Integer.class);
            result.put("payout",OwnerFinance.payout(money,pct==null?0:pct));
        }else{
            // The desk sees the day's work, not the spa's takings.
            result.remove("money"); result.remove("cashTrend");
            for(var row:services)row.remove("valueCents");
        }
        result.put("activeSubscriptions",db.queryForObject("SELECT COUNT(*) FROM user_spa_package_subscription WHERE spa_id=:spa AND status='ACTIVE' AND payment_status='SUCCESS' AND remaining_sessions>0 AND (expiry_date IS NULL OR expiry_date>UTC_TIMESTAMP())",args,Long.class));
        return result;
    }

    @GetMapping("/capacity")
    @PreAuthorize(OWNER)
    @Transactional(readOnly=true)
    public Map<String,Object> capacity(@PathVariable long spa,@RequestParam(defaultValue="TODAY") Period period,
            @RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to){
        var zone=zone(spa);var dates=range(period,LocalDate.now(zone),from,to);
        return capacity.report(spa,zone,dates.from,dates.to);
    }

    public enum View { APPOINTMENTS, VISITORS, BUYERS, TRANSACTIONS }
    @GetMapping("/records")
    @Transactional(readOnly=true)
    public Map<String,Object> records(@PathVariable long spa,@RequestParam(defaultValue="APPOINTMENTS") View view,
            @RequestParam(defaultValue="TODAY") Period period,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,
            @RequestParam(defaultValue="") String search,@RequestParam(defaultValue="") String status,
            @RequestParam(defaultValue="") String payment,@RequestParam(defaultValue="false") boolean pending,@RequestParam(defaultValue="false") boolean upcoming,
            @RequestParam(required=false) Long packageId,@RequestParam(required=false) Long serviceId,@RequestParam(required=false) Long therapistId,@RequestParam(defaultValue="0") int page) {
        if(page<0 || page>100000 || search.length()>120) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid search or page");
        if(view==View.TRANSACTIONS && !access.spa(spa)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only the owner can see the transactions");
        ZoneId zone=zone(spa); Dates dates=range(period,LocalDate.now(zone),from,to);
        var args=params(spa,zone,dates);
        args.put("offset",page*25); args.put("search","%"+search.trim().replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        args.put("status",status); args.put("payment",payment);
        String filter=" WHERE b.spa_id=:spa AND b.start_ts>=:start AND b.start_ts<:end ";
        if(!search.isBlank()) filter+=" AND (u.name LIKE :search ESCAPE '!' OR u.phone LIKE :search ESCAPE '!' OR b.booking_reference LIKE :search ESCAPE '!' OR s.name LIKE :search ESCAPE '!' OR t.name LIKE :search ESCAPE '!') ";
        // Guests who signed up by phone may have no name: match the phone too, and never filter on an empty search.
        String who=search.isBlank()?"":" AND (u.name LIKE :search ESCAPE '!' OR u.phone LIKE :search ESCAPE '!') ";
        if(serviceId!=null){filter+=" AND b.service_id=:serviceId ";args.put("serviceId",serviceId);}
        if(therapistId!=null){filter+=" AND b.therapist_id=:therapistId ";args.put("therapistId",therapistId);}
        if(!status.isBlank()) filter+=" AND b.status=:status ";
        if(!payment.isBlank()) filter+=" AND ("+STATE+")=:payment ";
        if(upcoming) filter+=" AND b.start_ts>=UTC_TIMESTAMP() AND b.status='CONFIRMED' ";
        if(pending) filter+=" AND c.status IN ('PENDING','NEEDS_CONTACT','NO_MOBILE') AND b.status='CONFIRMED' ";
        args.put("term",args.get("search"));args.put("packageId",packageId);
        String sql=switch(view) {
            case TRANSACTIONS -> "";
            case APPOINTMENTS -> "SELECT b.id,b.booking_reference AS reference,"+CUSTOMER+" AS customer,s.name AS service,"
                + " (SELECT o.channel FROM desk_origin o WHERE o.booking_id=b.id) AS deskSource, b.service_id AS serviceId,b.therapist_id AS therapistId,b.therapist_preference AS preference,b.therapist_type AS therapistType,"
                + " b.guest_count AS guests,t.name AS therapist,r.name AS room,b.status,c.status AS confirmation,"
                + " b.start_ts AS startUtc,b.end_ts AS endUtc,b.currency,"+OUTSTANDING+" AS payable,COALESCE(d.paid,0) AS deskPaid,COALESCE(d.tips,0) AS deskTips,"
                + " d.method AS deskMethod,d.collected_at AS deskCollectedUtc,COALESCE(d.returned,0) AS deskReturned,p.payment_status AS gatewayStatus,"
                + STATE+" AS payment "+JOINS+filter+" ORDER BY b.start_ts,b.id";
            case VISITORS -> "SELECT u.id,"+CUSTOMER+" AS customer,COUNT(*) AS visits,MAX(b.start_ts) AS lastUtc FROM booking b JOIN app_user u ON u.id=b.user_id "+
                "WHERE b.spa_id=:spa AND b.status='COMPLETED' AND b.start_ts>=:start AND b.start_ts<:end"+who+" GROUP BY u.id,u.name,u.phone ORDER BY lastUtc DESC,u.id";
            case BUYERS -> "SELECT x.id,x.user_id AS userId,"+CUSTOMER+" AS customer,x.level AS packageLevel,x.status,x.total_sessions AS sessions,"
                + " x.remaining_sessions AS remaining,x.purchase_date AS purchasedUtc,x.expiry_date AS expiresUtc,x.price_paid AS price,x.currency"
                + " FROM user_spa_package_subscription x JOIN app_user u ON u.id=x.user_id WHERE x.spa_id=:spa"
                + " AND x.payment_status='SUCCESS' AND (:packageId IS NULL OR x.spa_package_id=:packageId) AND x.purchase_date>=:start AND x.purchase_date<:end"
                + who+" ORDER BY x.purchase_date DESC,x.id";
        };
        var rows=view==View.TRANSACTIONS ? finance.entries(args,26,page*25) : db.queryForList(sql+" LIMIT 26 OFFSET :offset",args);
        // JDBC returns SQL DATETIME without an offset. Normalize all instants before serialization.
        for(var row:rows) for(var field:row.entrySet()) if(field.getKey().endsWith("Utc") && field.getValue()!=null)
            field.setValue(com.nirvana.application.service.SpaBookingAlerts.fromUtc(field.getValue()).atZone(zone).toOffsetDateTime());
        return Map.of("items",rows.subList(0,Math.min(25,rows.size())),"more",rows.size()>25,"timezone",zone.getId(),"dates",dates);
    }

    @GetMapping(value="/transactions.csv",produces="text/csv;charset=UTF-8")
    @PreAuthorize(OWNER)
    @Transactional(readOnly=true)
    public org.springframework.http.ResponseEntity<String> export(@PathVariable long spa,@RequestParam(defaultValue="TODAY") Period period,
            @RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(defaultValue="") String search) {
        ZoneId zone=zone(spa);
        var args=params(spa,zone,range(period,LocalDate.now(zone),from,to));
        args.put("term","%"+search.trim().replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        var rows=finance.entries(args,10001,0);
        if(rows.size()>10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Narrow the date range to export up to 10,000 transactions");
        // Times in the spa's own time zone, so a late-evening payment lands on the right day.
        var local=java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        StringBuilder csv=new StringBuilder("Date ("+zone.getId()+"),Reference,Receipt,Source,Held by,Method,Currency,Collected (minor units),Refunded (minor units),Tip (minor units)\r\n");
        for(var row:rows){
            if(row.get("eventUtc")!=null)row.put("eventUtc",com.nirvana.application.service.SpaBookingAlerts.fromUtc(row.get("eventUtc")).atZone(zone).format(local));
            row.put("channel","DESK".equals(row.get("channel"))?"Spa":"Nirvana (paid out)");
            var fields=new java.util.StringJoiner(",");
            for(String key:List.of("eventUtc","reference","receipt","source","channel","method","currency","collected","refunded","tip")){
                String value=Objects.toString(row.get(key),"");
                if(value.stripLeading().matches("^[=+@-].*") || value.startsWith("\t") || value.startsWith("\r")) value="'"+value;
                fields.add("\""+value.replace("\"","\"\"")+"\"");
            }
            csv.append(fields).append("\r\n");
        }
        return org.springframework.http.ResponseEntity.ok().header("Content-Disposition","attachment; filename=nirvana-transactions.csv").body(csv.toString());
    }

    /** The owner confirms the money taken at the desk for a cancelled appointment was given back, all parts at once. */
    @PostMapping("/bookings/{id}/return")
    @PreAuthorize(OWNER)
    @Transactional
    public void recordReturn(@PathVariable long spa,@PathVariable long id){
        requireOpenShift(spa,id);
        var booking=bookings.findForSpaUpdate(id,spa)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Appointment not found"));
        if(!"CANCELLED".equals(booking.getStatus().name()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only a cancelled appointment can have a desk return");
        var args=Map.of("spa",spa,"id",id,"actor",SecurityUtils.getCurrentUserId());
        if(db.queryForObject("SELECT COUNT(*) FROM spa_desk_receipt WHERE spa_id=:spa AND booking_id=:id",args,Integer.class)==0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"No desk receipt exists");
        if(db.update("UPDATE spa_desk_receipt SET returned_at=UTC_TIMESTAMP(6),returned_by=:actor WHERE spa_id=:spa AND booking_id=:id AND returned_at IS NULL",args)>0)
            audit.record(AdminActionType.UPDATE,"SPA_DESK_RECEIPT",id,SecurityUtils.getCurrentUserId(),null,Map.of("returned",true),"Desk refund recorded");
    }

    public record VoidInput(@jakarta.validation.constraints.NotBlank @Size(max=300) String reason){}
    /**
     * The owner undoes the latest desk payment for an appointment when it was recorded by mistake (wrong method,
     * amount or appointment), on the spa-local day it was recorded and before any money was returned.
     * The audit log keeps what was removed and why.
     */
    @PostMapping("/bookings/{id}/void")
    @PreAuthorize(OWNER)
    @Transactional
    public void voidReceipt(@PathVariable long spa,@PathVariable long id,@Valid @RequestBody VoidInput input){
        requireOpenShift(spa,id);
        bookings.findForSpaUpdate(id,spa)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Appointment not found"));
        var args=Map.<String,Object>of("spa",spa,"id",id);
        var receipts=db.queryForList("SELECT id,collected_at,returned_at,amount_cents,tip_cents,currency,method,receipt_reference,collected_by FROM spa_desk_receipt WHERE spa_id=:spa AND booking_id=:id ORDER BY id DESC FOR UPDATE",args);
        if(receipts.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"No desk payment is recorded for this appointment");
        if(receipts.stream().anyMatch(r->r.get("returned_at")!=null))throw new ResponseStatusException(HttpStatus.CONFLICT,"The money was already returned, so this payment can't be voided");
        var receipt=receipts.get(0);
        ZoneId zone=zone(spa);
        LocalDate recorded=com.nirvana.application.service.SpaBookingAlerts.fromUtc(receipt.get("collected_at")).atZone(zone).toLocalDate();
        if(!recorded.equals(LocalDate.now(zone)))throw new ResponseStatusException(HttpStatus.CONFLICT,"A desk payment can only be voided on the day it was recorded");
        db.update("DELETE FROM spa_desk_receipt WHERE id=:receipt",Map.of("receipt",receipt.get("id")));
        var removed=new LinkedHashMap<String,Object>();
        removed.put("amount",receipt.get("amount_cents")); removed.put("tip",receipt.get("tip_cents")); removed.put("currency",receipt.get("currency"));
        removed.put("method",receipt.get("method")); removed.put("reference",receipt.get("receipt_reference"));
        removed.put("collectedBy",receipt.get("collected_by"));
        audit.record(AdminActionType.DELETE,"SPA_DESK_RECEIPT",id,SecurityUtils.getCurrentUserId(),removed,null,input.reason.trim());
    }

    @GetMapping("/customers/{user}")
    @Transactional(readOnly=true)
    public Map<String,Object> history(@PathVariable long spa,@PathVariable long user) {
        var args=Map.of("spa",spa,"user",user);
        var visits=db.queryForList("SELECT b.booking_reference AS reference,s.name AS service,CONCAT(DATE_FORMAT(b.start_ts,'%Y-%m-%dT%H:%i:%s'),'Z') AS startUtc,b.status FROM booking b JOIN service s ON s.id=b.service_id WHERE b.user_id=:user AND b.spa_id=:spa ORDER BY b.start_ts DESC LIMIT 50",args);
        var purchases=db.queryForList("SELECT level,remaining_sessions AS remaining,status FROM user_spa_package_subscription WHERE user_id=:user AND spa_id=:spa AND payment_status='SUCCESS' ORDER BY purchase_date DESC LIMIT 50",args);
        var uses=db.queryForList("SELECT b.booking_reference AS reference,CONCAT(DATE_FORMAT(x.usage_date,'%Y-%m-%dT%H:%i:%s'),'Z') AS usedUtc FROM user_spa_package_usage_log x LEFT JOIN booking b ON b.id=x.booking_id AND b.spa_id=x.spa_id WHERE x.spa_id=:spa AND x.user_id=:user ORDER BY x.usage_date DESC LIMIT 50",args);
        return Map.of("visits",visits,"packages",purchases,"redemptions",uses);
    }
    @GetMapping("/package-sales")
    @PreAuthorize(OWNER)
    public List<Map<String,Object>> sales(@PathVariable long spa){
        return db.queryForList("SELECT spa_package_id AS id,COALESCE(currency,'INR') AS currency,SUM(price_paid) AS sales FROM user_spa_package_subscription WHERE spa_id=:spa AND payment_status='SUCCESS' GROUP BY spa_package_id,COALESCE(currency,'INR')",Map.of("spa",spa));
    }
    @GetMapping("/packages")
    @PreAuthorize(OWNER)
    public List<Map<String,Object>> packages(@PathVariable long spa) {
        return db.queryForList("""
            SELECT p.id,(SELECT COUNT(*) FROM user_spa_package_usage_log l JOIN user_spa_package_subscription q ON q.id=l.user_spa_package_subscription_id WHERE l.spa_id=:spa AND q.spa_package_id=p.id) AS used,COUNT(x.id) AS sold, COUNT(DISTINCT x.user_id) AS buyers,
              COUNT(CASE WHEN x.status='ACTIVE' AND x.remaining_sessions>0 AND (x.expiry_date IS NULL OR x.expiry_date>UTC_TIMESTAMP()) THEN 1 END) AS active,
              COALESCE(SUM(x.total_sessions),0) AS purchased,COALESCE(SUM(x.remaining_sessions),0) AS remaining,
              COUNT(CASE WHEN x.status='ACTIVE' AND x.expiry_date BETWEEN UTC_TIMESTAMP() AND DATE_ADD(UTC_TIMESTAMP(),INTERVAL 30 DAY) THEN 1 END) AS expiring
            FROM spa_packages p LEFT JOIN user_spa_package_subscription x ON x.spa_package_id=p.id AND x.spa_id=p.spa_id AND x.payment_status='SUCCESS'
            WHERE p.spa_id=:spa GROUP BY p.id ORDER BY p.id
            """,Map.of("spa",spa));
    }
    @DeleteMapping("/packages/{id}")
    @PreAuthorize(OWNER)
    @Transactional
    public void deleteUnusedPackage(@PathVariable long spa,@PathVariable long id){
        var args=Map.of("spa",spa,"id",id);
        int removed;
        try { removed=db.update("DELETE FROM spa_packages WHERE id=:id AND spa_id=:spa AND status='INACTIVE' AND published_once=0 AND NOT EXISTS (SELECT 1 FROM user_spa_package_subscription WHERE spa_package_id=:id)",args); }
        catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Something else still points at it (a cart, an order, a log): it has history, so it stays.
            throw new ResponseStatusException(HttpStatus.CONFLICT,"This package is linked to other records, so it can't be deleted. Archive it instead.");
        }
        if(removed!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Only an inactive package with no purchase history can be deleted");
        audit.record(AdminActionType.DELETE,"SPA_PACKAGE",id,SecurityUtils.getCurrentUserId(),null,null,"Removed unused package");
    }
    @GetMapping("/bookings/{id}/history")
    public List<Map<String,Object>> bookingHistory(@PathVariable long spa,@PathVariable long id){
        if (!bookings.existsByIdAndSpa_Id(id,spa)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Appointment not found");
        return db.queryForList("""
            SELECT h.action_type AS action,h.entity_type AS entity,h.actor_user_id AS actor,
              CONCAT(DATE_FORMAT(h.created_at,'%Y-%m-%dT%H:%i:%s'),'Z') AS atUtc,h.reason
            FROM admin_action_log h JOIN booking b ON b.id=h.entity_id
            WHERE b.spa_id=:spa AND b.id=:id AND UPPER(h.entity_type) IN ('BOOKING','SPA_DESK_RECEIPT')
            ORDER BY h.created_at DESC,h.id DESC LIMIT 50
            """,Map.of("spa",spa,"id",id));
    }
    public record Reply(@NotNull Boolean accept,@Size(max=300) String reason){}
    @PostMapping("/bookings/{id}/reply")
    @Transactional
    public void reply(@PathVariable long spa,@PathVariable long id,@Valid @RequestBody Reply input){
        var b=bookings.findForSpaUpdate(id,spa)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Appointment not found"));
        if(!"CONFIRMED".equals(b.getStatus().name()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Refresh the booking before replying");
        var tokens=db.queryForList("SELECT token FROM spa_booking_confirmation WHERE spa_id=:spa AND booking_id=:id AND status IN ('PENDING','NEEDS_CONTACT','NO_MOBILE') FOR UPDATE",Map.of("spa",spa,"id",id),String.class);
        if(tokens.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"The spa reply is already recorded or closed. Refresh this appointment.");
        if(input.accept)replies.confirm(tokens.get(0));else replies.decline(tokens.get(0),input.reason);
        audit.record(AdminActionType.STATUS_CHANGE,"BOOKING",id,SecurityUtils.getCurrentUserId(),null,Map.of("accepted",input.accept),input.reason);
    }
    private void requireOpenShift(long spa,long booking){
        var args=Map.of("spa",spa,"id",booking);
        var tz=db.queryForObject("SELECT timezone FROM spa WHERE id=:spa FOR UPDATE",args,String.class);
        var day=LocalDate.now(ZoneId.of(Objects.toString(tz,"Asia/Kolkata")));
        var states=db.queryForList("SELECT state FROM spa_financial_report WHERE spa_id=:spa AND business_date=:day AND currency=(SELECT currency FROM booking WHERE id=:id AND spa_id=:spa) AND kind='SHIFT' ORDER BY revision DESC LIMIT 1",Map.of("spa",spa,"id",booking,"day",day),String.class);
        if(!states.isEmpty() && !"REOPENED".equals(states.get(0)))throw new IllegalStateException("Daily shift is closed. Ask the owner to reopen it before changing receipts.");
    }
    public enum Method { CASH, UPI, CARD }
    /**
     * Money taken at the desk. Without amountCents it is the whole balance; a part payment (an advance)
     * needs a requestKey, unique per submission, so a retried request isn't recorded twice. A tip is
     * recorded with it but kept apart from the service amount.
     */
    public record Collection(@NotNull Method method,@Size(max=80) String reference,
            @jakarta.validation.constraints.Positive Integer amountCents,@jakarta.validation.constraints.PositiveOrZero Integer tipCents,
            @Size(max=64) String requestKey) {}
    @PostMapping("/bookings/{id}/collect")
    @Transactional
    public void collect(@PathVariable long spa,@PathVariable long id,@Valid @RequestBody Collection input) {
        db.queryForObject("SELECT id FROM spa WHERE id=:spa FOR UPDATE",Map.of("spa",spa),Long.class);
        var booking=bookings.findForSpaUpdate(id,spa)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Appointment not found"));
        var args=new HashMap<String,Object>(); args.put("id",id); args.put("spa",spa); args.put("key",input.requestKey);
        if(input.requestKey!=null && db.queryForObject("SELECT COUNT(*) FROM spa_desk_receipt WHERE spa_id=:spa AND request_key=:key",args,Integer.class)>0) return;
        requireOpenShift(spa,id);
        var row=db.queryForMap("SELECT "+STATE+" AS state,"+OUTSTANDING+" AS outstanding,COALESCE(d.receipts,0) AS receipts "+JOINS+" WHERE b.id=:id AND b.spa_id=:spa",args);
        long outstanding=((Number)row.get("outstanding")).longValue();
        if(!"DUE_AT_SPA".equals(row.get("state"))){
            // Replaying a full collection that already went through changes nothing.
            if(input.amountCents==null && ((Number)row.get("receipts")).longValue()>0) return;
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Refresh the appointment. No payment is due at the spa.");
        }
        long amount=input.amountCents==null?outstanding:input.amountCents;
        if(amount>outstanding) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"That's more than the balance still due");
        if(amount<outstanding && input.requestKey==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A part payment needs a request key");
        args.put("actor",SecurityUtils.getCurrentUserId()); args.put("amount",amount); args.put("tip",input.tipCents==null?0:input.tipCents);
        args.put("currency",booking.getCurrency()); args.put("method",input.method.name()); args.put("reference",input.reference);
        db.update("INSERT INTO spa_desk_receipt(booking_id,spa_id,collected_by,collected_at,amount_cents,tip_cents,currency,method,receipt_reference,request_key) VALUES(:id,:spa,:actor,UTC_TIMESTAMP(6),:amount,:tip,:currency,:method,:reference,:key)",args);
        audit.record(AdminActionType.CREATE,"SPA_DESK_RECEIPT",id,SecurityUtils.getCurrentUserId(),null,Map.of("amount",amount,"tip",args.get("tip"),"method",input.method),
            amount<outstanding?"Part payment received at spa":"Payment received at spa");
    }

    public record Move(@NotNull Long slotId,@Size(max=300) String reason) {}
    @PostMapping("/bookings/{id}/move")
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void move(@PathVariable long spa,@PathVariable long id,@Valid @RequestBody Move input) {
        var booking=bookings.findForSpaUpdate(id,spa)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Appointment not found"));
        if(!"CONFIRMED".equals(booking.getStatus().name()) || input.slotId.equals(booking.getSlot().getId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Choose a new time for a confirmed appointment");
        var request=new BookingRescheduleRequest();
        request.setTargetSlotId(input.slotId); request.setReason(input.reason);
        long previous=booking.getSlot().getId();
        bookingService.rescheduleBySpa(id,spa,request);
        audit.record(AdminActionType.UPDATE,"BOOKING",id,SecurityUtils.getCurrentUserId(),Map.of("slot",previous),Map.of("slot",input.slotId),input.reason);
    }
}
