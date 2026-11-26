package com.nirvana.application.controller;

import com.nirvana.application.model.User;
import com.nirvana.application.model.dto.BookingCreateRequest;
import com.nirvana.application.model.enums.RoleType;
import com.nirvana.application.repository.*;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.impl.BookingServiceImpl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Transactional
public class AppointmentOperations {
    private final JdbcTemplate db;
    private final SpaRepository spas;
    private final UserRepository users;
    private final SlotRepository slots;
    private final com.nirvana.application.service.impl.SessionAllocation allocation;
    private final RoleRepository roles;
    private final BookingServiceImpl booking;
    private long actor(){ return SecurityUtils.getCurrentUserId(); }
    private void lock(long spa){ spas.lockSchedule(spa).orElseThrow(); }
    @GetMapping("/admin/finance/spas") @PreAuthorize("@staffAccess.admin()")
    public List<Map<String,Object>> financeSpas(@RequestParam(defaultValue="") String search){
        return db.queryForList("SELECT id,name,is_active AS active FROM spa WHERE LOCATE(?,name)>0 OR CAST(id AS CHAR)=? ORDER BY name LIMIT 50",search,search);
    }
    public enum Mode { ENQUIRY, REQUEST, INSTANT }
    public record Policy(@NotNull Mode mode, boolean verified){}
    @GetMapping("/spas/{spa}/booking-policy")
    public Map<String,Object> policy(@PathVariable long spa){
        return db.queryForMap("SELECT booking_mode AS mode,phone,email,timezone FROM spa WHERE id=? AND is_active=1",spa);
    }
    @PutMapping("/owner/spas/{spa}/booking-policy")
    @PreAuthorize("@staffAccess.spa(#spa)")
    public void policy(@PathVariable long spa,@Valid @RequestBody Policy input){
        var s=spas.lockSchedule(spa).orElseThrow();
        if(input.mode()!=Mode.ENQUIRY && (!input.verified() || !Boolean.TRUE.equals(s.getIsVerified()) ||
                (s.getEmail()==null || s.getEmail().isBlank()) && (s.getPhone()==null || s.getPhone().isBlank())))
            throw new IllegalArgumentException("Verify service prices, capacity, opening hours and a working contact first");
        s.setBookingMode(input.mode().name());
        db.update("INSERT INTO booking_policy_history(spa_id,mode,actor,updated_at) VALUES(?,?,?,UTC_TIMESTAMP())",spa,input.mode().name(),actor());
    }
    @GetMapping("/me/checkout-attempts/{key}")
    public Map<String,Object> recover(@PathVariable String key,@RequestParam long spa){
        var rows=db.queryForList("SELECT b.id,b.booking_reference AS reference,b.status FROM checkout_attempt a JOIN booking b ON b.id=a.booking_id WHERE a.spa_id=? AND a.request_key=? AND a.user_id=?",spa,key,actor());
        return rows.isEmpty()?Map.of("status","UNKNOWN"):rows.get(0);
    }
    public record RequestTime(@NotNull Long slotId){}
    @PostMapping("/spas/{spa}/appointment-requests")
    public Map<String,Object> request(@PathVariable long spa,@Valid @RequestBody RequestTime input){
        lock(spa);
        db.update("""
            INSERT INTO appointment_request(spa_id,slot_id,user_id,state,deadline)
            SELECT s.spa_id,s.id,?,'PENDING',LEAST(s.start_ts,UTC_TIMESTAMP()+INTERVAL 2 HOUR)
            FROM slot s JOIN spa p ON p.id=s.spa_id WHERE p.id=? AND s.id=? AND p.booking_mode='REQUEST'
            AND p.is_active=1 AND s.is_blocked=0 AND s.booked_units<s.capacity_unit AND s.start_ts>UTC_TIMESTAMP()
            ON DUPLICATE KEY UPDATE id=appointment_request.id
            """,actor(),spa,input.slotId());
        var found=db.queryForList("SELECT id,deadline FROM appointment_request WHERE spa_id=? AND slot_id=? AND user_id=?",spa,input.slotId(),actor());
        if(found.isEmpty())throw new IllegalStateException("This time is unavailable for requests");
        return found.get(0);
    }
    private static final String REQUESTS="""
        SELECT r.id,r.booking_id AS bookingId,p.name AS spaName,s.name AS service,
        DATE_FORMAT(l.start_ts,'%Y-%m-%dT%H:%i:%sZ') AS starts,
        DATE_FORMAT(r.deadline,'%Y-%m-%dT%H:%i:%sZ') AS deadline,
        CASE WHEN r.state='PENDING' AND r.deadline<=UTC_TIMESTAMP() THEN 'EXPIRED' ELSE r.state END AS status
        FROM appointment_request r JOIN spa p ON p.id=r.spa_id JOIN slot l ON l.id=r.slot_id JOIN service s ON s.id=l.service_id
        """;
    @GetMapping("/me/appointment-requests")
    public List<Map<String,Object>> mine(){return db.queryForList(REQUESTS+" WHERE r.user_id=? ORDER BY r.id DESC LIMIT 100",actor());}
    @GetMapping("/owner/spas/{spa}/appointment-requests")
    @PreAuthorize("@staffAccess.desk(#spa)")
    public List<Map<String,Object>> inbox(@PathVariable long spa){return db.queryForList(REQUESTS+" WHERE r.spa_id=? ORDER BY (r.state='PENDING' AND r.deadline>UTC_TIMESTAMP()) DESC,r.id DESC LIMIT 100",spa);}
    public record Answer(@NotNull Boolean accept){}
    @PostMapping("/owner/spas/{spa}/appointment-requests/{id}")
    @PreAuthorize("@staffAccess.desk(#spa)")
    public void answer(@PathVariable long spa,@PathVariable long id,@Valid @RequestBody Answer input){
        lock(spa);
        var rows=db.queryForList("SELECT user_id,slot_id FROM appointment_request WHERE spa_id=? AND id=? AND state='PENDING' AND deadline>UTC_TIMESTAMP() FOR UPDATE",spa,id);
        if(rows.isEmpty())throw new IllegalStateException("Request expired or already answered");
        var row=rows.get(0);
        Long booked=input.accept()?reserve(spa,num(row,"slot_id"),num(row,"user_id"),null,"request-"+id):null;
        db.update("UPDATE appointment_request SET state=?,booking_id=?,answered_by=? WHERE id=?",input.accept()?"ACCEPTED":"DECLINED",booked,actor(),id);
    }
    static long num(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    private long reserve(long spa,long slot,long user,Long therapist,String key){
        var services=db.queryForList("SELECT service_id FROM slot WHERE id=? AND spa_id=?",Long.class,slot,spa);
        if(services.isEmpty())throw new IllegalArgumentException("Choose a slot from this spa");
        var input=new BookingCreateRequest();
        input.setSpaId(spa);input.setServiceId(services.get(0));input.setSlotId(slot);input.setGuests(1);
        input.setPaymentMode("OFFLINE");input.setTherapistId(therapist);input.setRequestKey(key);
        return booking.createFromDesk(user,input).getBookingId();
    }
    public record DeskAppointment(@NotNull Long slotId,Long therapistId,Long guestId,@Size(max=100) String name,
        @Size(max=100) String contact,@NotNull @Pattern(regexp="WALK_IN|PHONE") String channel,
        @NotNull @Pattern(regexp="[A-Za-z0-9_-]{8,64}") String requestKey){}
    @GetMapping("/owner/spas/{spa}/desk-rooms")
    @PreAuthorize("@staffAccess.desk(#spa)")
    public List<Map<String,Object>> roomChoices(@PathVariable long spa,@RequestParam long service,@RequestParam java.time.LocalDate date){
        var zone=java.time.ZoneId.of(Objects.toString(db.queryForObject("SELECT timezone FROM spa WHERE id=?",String.class,spa),"Asia/Kolkata"));
        return db.queryForList("SELECT s.id,r.name AS room FROM slot s JOIN spa_room r ON r.id=s.room_id AND r.spa_id=s.spa_id WHERE s.spa_id=? AND s.service_id=? AND s.start_ts>=? AND s.start_ts<? ORDER BY s.start_ts,r.name",spa,service,
            java.sql.Timestamp.from(date.atStartOfDay(zone).toInstant()),java.sql.Timestamp.from(date.plusDays(1).atStartOfDay(zone).toInstant()));
    }
    public record TherapistOption(long id,String name){}
    @GetMapping("/owner/spas/{spa}/desk-slots/{slot}/therapists")
    @PreAuthorize("@staffAccess.desk(#spa)")
    public List<TherapistOption> staffChoices(@PathVariable long spa,@PathVariable long slot){
        var selected=slots.findById(slot).filter(s->s.getSpa().getId().equals(spa)).orElseThrow();
        return allocation.eligible(selected,null,null).stream().map(t->new TherapistOption(t.getId(),t.getName())).toList();
    }
    @GetMapping("/owner/spas/{spa}/desk-guests")
    @PreAuthorize("@staffAccess.desk(#spa)")
    public List<Map<String,Object>> guests(@PathVariable long spa,@RequestParam String search){
        if(search.isBlank())return List.of();
        return db.queryForList("""
            SELECT DISTINCT u.id,COALESCE(u.name,u.display_name,'Guest') AS name FROM app_user u
            JOIN booking b ON b.user_id=u.id LEFT JOIN desk_guest g ON g.user_id=u.id AND g.spa_id=b.spa_id
            WHERE b.spa_id=? AND (LOCATE(?,u.name)>0 OR u.phone=? OR u.email=? OR g.contact=?) ORDER BY name LIMIT 20
            """,spa,search,search,search,search);
    }
    @PostMapping("/owner/spas/{spa}/desk-appointments")
    @PreAuthorize("@staffAccess.desk(#spa)")
    public long add(@PathVariable long spa,@Valid @RequestBody DeskAppointment input){
        lock(spa);
        var previous=db.queryForList("SELECT booking_id FROM desk_origin WHERE spa_id=? AND request_key=?",Long.class,spa,input.requestKey());
        if(!previous.isEmpty())return previous.get(0);
        Long guest=input.guestId();
        if(guest==null){
            if(input.name()==null || input.name().isBlank())throw new IllegalArgumentException("Enter the guest name");
            var profile=User.builder().name(input.name().trim()).role(roles.findByRoleType(RoleType.CUSTOMER))
                .password("!desk-profile").active(false).isActive(false).marketingOptIn(false).build();
            guest=users.saveAndFlush(profile).getId();
            db.update("INSERT INTO desk_guest(spa_id,user_id,guest_name,contact) VALUES(?,?,?,?)",spa,guest,profile.getName(),input.contact());
        } else if(db.queryForObject("SELECT COUNT(*) FROM booking WHERE spa_id=? AND user_id=?",Integer.class,spa,guest)==0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Guest not found at this spa");
        long id=reserve(spa,input.slotId(),guest,input.therapistId(),input.requestKey());
        db.update("INSERT INTO desk_origin(booking_id,spa_id,request_key,channel,actor) VALUES(?,?,?,?,?)",id,spa,input.requestKey(),input.channel(),actor());
        return id;
    }
}
