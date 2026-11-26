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
@PreAuthorize("@staffAccess.spa(#spa)")
@RequiredArgsConstructor
public class OwnerDeskController {
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
        LEFT JOIN payment p ON p.booking_id=b.id LEFT JOIN spa_desk_receipt d ON d.booking_id=b.id
        LEFT JOIN spa_booking_confirmation c ON c.booking_id=b.id AND c.spa_id=b.spa_id
        """;
    private static final String STATE = """
        CASE WHEN b.payment_type='PAID_BY_PACKAGE' THEN 'PACKAGE'
             WHEN d.returned_at IS NOT NULL THEN 'RETURNED_AT_SPA'
             WHEN d.booking_id IS NOT NULL THEN 'PAID_AT_SPA'
             WHEN p.payment_status IN ('CAPTURED','COMPLETED') THEN 'PAID'
             WHEN p.payment_status IN ('REFUNDED','PARTIALLY_REFUNDED') THEN p.payment_status
             WHEN b.payment_mode='OFFLINE' AND b.status IN ('ARRIVED','CONFIRMED','COMPLETED') AND b.remainder_cents>0 THEN 'DUE_AT_SPA'
             ELSE COALESCE(p.payment_status,'UNPAID') END
        """;
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
              COUNT(CASE WHEN c.status='PENDING' AND b.status='CONFIRMED' THEN 1 END) AS pending
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
        var due=db.queryForList("SELECT b.currency,SUM(b.remainder_cents) AS outstanding "+JOINS+
            " WHERE b.spa_id=:spa AND b.start_ts>=:start AND b.start_ts<:end AND ("+STATE+")='DUE_AT_SPA' GROUP BY b.currency",args);
        var result=new LinkedHashMap<String,Object>();
        var todayArgs=params(spa,zone,new Dates(today,today));
        result.put("arrivals",db.queryForObject("SELECT COUNT(*) FROM booking WHERE spa_id=:spa AND status='CONFIRMED' AND start_ts>=UTC_TIMESTAMP() AND start_ts<:end",todayArgs,Long.class));
        result.put("conflicts",db.queryForObject("""
            SELECT COUNT(*) FROM booking b WHERE b.spa_id=:spa AND b.start_ts<:end AND b.end_ts>:start AND b.status IN ('CONFIRMED','ARRIVED')
            AND (EXISTS(SELECT 1 FROM booking o WHERE o.spa_id=b.spa_id AND o.id<>b.id AND o.start_ts<b.end_ts AND o.end_ts>b.start_ts
                AND o.status IN ('CONFIRMED','ARRIVED') AND (o.room_id=b.room_id OR o.therapist_id=b.therapist_id))
                OR EXISTS(SELECT 1 FROM closure c WHERE c.spa_id=b.spa_id AND c.start_ts<b.end_ts AND c.end_ts>b.start_ts))
            """,todayArgs,Long.class));
        result.put("timezone",zone.getId()); result.put("today",today); result.put("dates",dates);
        result.put("counts",counts(args)); result.put("previous",counts(params(spa,zone,new Dates(dates.from.minusDays(days),dates.from.minusDays(1)))));
        result.put("cashTrend",finance.trend(args,String.join(" UNION ALL ",buckets))); result.put("packagesTrend",db.queryForList("SELECT days.day,(SELECT COUNT(*) FROM user_spa_package_subscription x WHERE x.spa_id=:spa AND x.payment_status='SUCCESS' AND x.purchase_date>=days.lo AND x.purchase_date<days.hi) AS sales,(SELECT COUNT(*) FROM user_spa_package_usage_log l WHERE l.spa_id=:spa AND l.usage_date>=days.lo AND l.usage_date<days.hi) AS redemptions FROM ("+String.join(" UNION ALL ",buckets)+") days ORDER BY days.day",args)); result.put("due",due); result.put("trend",trend); result.put("services",services); result.put("money",money);
        result.put("activeSubscriptions",db.queryForObject("SELECT COUNT(*) FROM user_spa_package_subscription WHERE spa_id=:spa AND status='ACTIVE' AND payment_status='SUCCESS' AND remaining_sessions>0 AND (expiry_date IS NULL OR expiry_date>UTC_TIMESTAMP())",args,Long.class));
        return result;
    }

    @GetMapping("/capacity")
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
        ZoneId zone=zone(spa); Dates dates=range(period,LocalDate.now(zone),from,to);
        var args=params(spa,zone,dates);
        args.put("offset",page*25); args.put("search","%"+search.trim().replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        args.put("status",status); args.put("payment",payment);
        String filter=" WHERE b.spa_id=:spa AND b.start_ts>=:start AND b.start_ts<:end ";
        if(!search.isBlank()) filter+=" AND (u.name LIKE :search ESCAPE '!' OR b.booking_reference LIKE :search ESCAPE '!' OR s.name LIKE :search ESCAPE '!' OR t.name LIKE :search ESCAPE '!') ";
        if(serviceId!=null){filter+=" AND b.service_id=:serviceId ";args.put("serviceId",serviceId);}
        if(therapistId!=null){filter+=" AND b.therapist_id=:therapistId ";args.put("therapistId",therapistId);}
        if(!status.isBlank()) filter+=" AND b.status=:status ";
        if(!payment.isBlank()) filter+=" AND ("+STATE+")=:payment ";
        if(upcoming) filter+=" AND b.start_ts>=UTC_TIMESTAMP() AND b.status='CONFIRMED' ";
        if(pending) filter+=" AND c.status='PENDING' AND b.status='CONFIRMED' ";
        args.put("term",args.get("search"));args.put("packageId",packageId);
        String sql=switch(view) {
            case TRANSACTIONS -> "";
            case APPOINTMENTS -> """
                SELECT b.id,b.booking_reference AS reference,u.name AS customer,s.name AS service,
                b.service_id AS serviceId,b.therapist_id AS therapistId,b.therapist_preference AS preference,b.therapist_type AS therapistType,
                b.guest_count AS guests,t.name AS therapist,r.name AS room,b.status,c.status AS confirmation,
                b.start_ts AS startUtc,b.end_ts AS endUtc,b.currency,b.remainder_cents AS payable,
                d.method AS deskMethod,p.payment_status AS gatewayStatus,
                """+STATE+" AS payment "+JOINS+filter+" ORDER BY b.start_ts,b.id";
            case VISITORS -> "SELECT u.id,u.name AS customer,COUNT(*) AS visits,MAX(b.start_ts) AS lastUtc FROM booking b JOIN app_user u ON u.id=b.user_id JOIN service s ON s.id=b.service_id LEFT JOIN therapist t ON t.id=b.therapist_id "+
                "WHERE b.spa_id=:spa AND b.status='COMPLETED' AND b.start_ts>=:start AND b.start_ts<:end AND u.name LIKE :search ESCAPE '!' GROUP BY u.id,u.name ORDER BY lastUtc DESC,u.id";
            case BUYERS -> """
                SELECT x.id,x.user_id AS userId,u.name AS customer,x.level AS packageLevel,x.status,x.total_sessions AS sessions,
                x.remaining_sessions AS remaining,x.purchase_date AS purchasedUtc,x.expiry_date AS expiresUtc,x.price_paid AS price,x.currency
                FROM user_spa_package_subscription x JOIN app_user u ON u.id=x.user_id WHERE x.spa_id=:spa
                AND x.payment_status='SUCCESS' AND (:packageId IS NULL OR x.spa_package_id=:packageId) AND x.purchase_date>=:start AND x.purchase_date<:end AND u.name LIKE :search ESCAPE '!'
                ORDER BY x.purchase_date DESC,x.id
                """;
        };
        var rows=view==View.TRANSACTIONS ? finance.entries(args,26,page*25) : db.queryForList(sql+" LIMIT 26 OFFSET :offset",args);
        // JDBC returns SQL DATETIME without an offset. Normalize all instants before serialization.
        for(var row:rows) for(var field:row.entrySet()) if(field.getKey().endsWith("Utc") && field.getValue()!=null){
            var value=field.getValue();
            LocalDateTime local=value instanceof java.sql.Timestamp ts?ts.toLocalDateTime():(LocalDateTime)value;
            field.setValue(local.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toOffsetDateTime());
        }
        return Map.of("items",rows.subList(0,Math.min(25,rows.size())),"more",rows.size()>25,"timezone",zone.getId(),"dates",dates);
    }

    @GetMapping(value="/transactions.csv",produces="text/csv;charset=UTF-8")
    @Transactional(readOnly=true)
    public org.springframework.http.ResponseEntity<String> export(@PathVariable long spa,@RequestParam(defaultValue="TODAY") Period period,
            @RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(defaultValue="") String search) {
        ZoneId zone=zone(spa);
        var args=params(spa,zone,range(period,LocalDate.now(zone),from,to));
        args.put("term","%"+search.trim().replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        var rows=finance.entries(args,10001,0);
        if(rows.size()>10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Narrow the date range to export up to 10,000 transactions");
        StringBuilder csv=new StringBuilder("UTC date,Reference,Receipt,Source,Method,Currency,Collected (minor units),Refunded (minor units)\r\n");
        for(var row:rows){
            var fields=new java.util.StringJoiner(",");
            for(String key:List.of("eventUtc","reference","receipt","source","method","currency","collected","refunded")){
                String value=Objects.toString(row.get(key),"");
                if(value.stripLeading().matches("^[=+@-].*") || value.startsWith("\t") || value.startsWith("\r")) value="'"+value;
                fields.add("\""+value.replace("\"","\"\"")+"\"");
            }
            csv.append(fields).append("\r\n");
        }
        return org.springframework.http.ResponseEntity.ok().header("Content-Disposition","attachment; filename=nirvana-transactions.csv").body(csv.toString());
    }

    @PostMapping("/bookings/{id}/return")
    @Transactional
    public void recordReturn(@PathVariable long spa,@PathVariable long id){
        var booking=bookings.findByIdForUpdate(id).filter(b->b.getSpa().getId()==spa)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Appointment not found"));
        if(!"CANCELLED".equals(booking.getStatus().name()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only a cancelled appointment can have a desk return");
        var args=Map.of("spa",spa,"id",id,"actor",SecurityUtils.getCurrentUserId());
        if(db.queryForObject("SELECT COUNT(*) FROM spa_desk_receipt WHERE spa_id=:spa AND booking_id=:id",args,Integer.class)==0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"No desk receipt exists");
        if(db.update("UPDATE spa_desk_receipt SET returned_at=UTC_TIMESTAMP(6),returned_by=:actor WHERE spa_id=:spa AND booking_id=:id AND returned_at IS NULL",args)==1)
            audit.record(AdminActionType.UPDATE,"SPA_DESK_RECEIPT",id,SecurityUtils.getCurrentUserId(),null,Map.of("returned",true),"Desk refund recorded");
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
    public List<Map<String,Object>> sales(@PathVariable long spa){
        return db.queryForList("SELECT spa_package_id AS id,COALESCE(currency,'INR') AS currency,SUM(price_paid) AS sales FROM user_spa_package_subscription WHERE spa_id=:spa AND payment_status='SUCCESS' GROUP BY spa_package_id,COALESCE(currency,'INR')",Map.of("spa",spa));
    }
    @GetMapping("/packages")
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
    @Transactional
    public void deleteUnusedPackage(@PathVariable long spa,@PathVariable long id){
        var args=Map.of("spa",spa,"id",id);
        int removed=db.update("DELETE FROM spa_packages WHERE id=:id AND spa_id=:spa AND status='INACTIVE' AND published_once=0 AND NOT EXISTS (SELECT 1 FROM user_spa_package_subscription WHERE spa_package_id=:id)",args);
        if(removed!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Only an inactive package with no purchase history can be deleted");
        audit.record(AdminActionType.DELETE,"SPA_PACKAGE",id,SecurityUtils.getCurrentUserId(),null,null,"Removed unused package");
    }
    @GetMapping("/bookings/{id}/history")
    public List<Map<String,Object>> bookingHistory(@PathVariable long spa,@PathVariable long id){
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
        var b=bookings.findByIdForUpdate(id).filter(v->v.getSpa().getId()==spa)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Appointment not found"));
        if(!"CONFIRMED".equals(b.getStatus().name()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Refresh the booking before replying");
        var tokens=db.queryForList("SELECT token FROM spa_booking_confirmation WHERE spa_id=:spa AND booking_id=:id AND status='PENDING' FOR UPDATE",Map.of("spa",spa,"id",id),String.class);
        if(tokens.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"The spa reply is already recorded or closed. Refresh this appointment.");
        if(input.accept)replies.confirm(tokens.get(0));else replies.decline(tokens.get(0),input.reason);
        audit.record(AdminActionType.STATUS_CHANGE,"BOOKING",id,SecurityUtils.getCurrentUserId(),null,Map.of("accepted",input.accept),input.reason);
    }
    public enum Method { CASH, UPI, CARD }
    public record Collection(@NotNull Method method,@Size(max=80) String reference) {}
    @PostMapping("/bookings/{id}/collect")
    @Transactional
    public void collect(@PathVariable long spa,@PathVariable long id,@Valid @RequestBody Collection input) {
        var booking=bookings.findByIdForUpdate(id).filter(b->b.getSpa().getId()==spa)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Appointment not found"));
        var args=new HashMap<String,Object>(); args.put("id",id); args.put("spa",spa);
        if(db.queryForObject("SELECT COUNT(*) FROM spa_desk_receipt WHERE booking_id=:id",args,Integer.class)>0) return;
        var state=db.queryForObject("SELECT "+STATE+" "+JOINS+" WHERE b.id=:id AND b.spa_id=:spa",args,String.class);
        if(!"DUE_AT_SPA".equals(state)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Refresh the appointment. No payment is due at the spa.");
        args.put("actor",SecurityUtils.getCurrentUserId()); args.put("amount",booking.getRemainderCents());
        args.put("currency",booking.getCurrency()); args.put("method",input.method.name()); args.put("reference",input.reference);
        db.update("INSERT INTO spa_desk_receipt(booking_id,spa_id,collected_by,collected_at,amount_cents,currency,method,receipt_reference) VALUES(:id,:spa,:actor,UTC_TIMESTAMP(6),:amount,:currency,:method,:reference)",args);
        audit.record(AdminActionType.CREATE,"SPA_DESK_RECEIPT",id,SecurityUtils.getCurrentUserId(),null,Map.of("amount",booking.getRemainderCents(),"method",input.method),"Payment received at spa");
    }

    public record Move(@NotNull Long slotId,@Size(max=300) String reason) {}
    @PostMapping("/bookings/{id}/move")
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void move(@PathVariable long spa,@PathVariable long id,@Valid @RequestBody Move input) {
        var booking=bookings.findByIdForUpdate(id).filter(b->b.getSpa().getId()==spa)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Appointment not found"));
        if(!"CONFIRMED".equals(booking.getStatus().name()) || input.slotId.equals(booking.getSlot().getId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Choose a new time for a confirmed appointment");
        var request=new BookingRescheduleRequest();
        request.setTargetSlotId(input.slotId); request.setReason(input.reason);
        long previous=booking.getSlot().getId();
        bookingService.rescheduleBooking(id,booking.getUser().getId(),request);
        audit.record(AdminActionType.UPDATE,"BOOKING",id,SecurityUtils.getCurrentUserId(),Map.of("slot",previous),Map.of("slot",input.slotId),input.reason);
    }
}
