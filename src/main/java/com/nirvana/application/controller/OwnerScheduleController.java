package com.nirvana.application.controller;

import com.nirvana.application.model.*;
import com.nirvana.application.model.dto.SlotAvailabilityResponse;
import com.nirvana.application.model.enums.*;
import com.nirvana.application.repository.*;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.AuditTrailService;
import com.nirvana.application.service.impl.SlotAvailabilityService;
import com.nirvana.application.service.impl.TherapistRoster;
import com.nirvana.application.service.impl.TherapistRoster.Interval;
import com.nirvana.application.service.impl.TherapistRoster.Key;
import com.nirvana.application.service.impl.TherapistRoster.Kind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/owner/spas/{spaId}/schedule")
@RequiredArgsConstructor
@PreAuthorize("@staffAccess.spa(#spaId)")
@Transactional(isolation=Isolation.READ_COMMITTED)
public class OwnerScheduleController {
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final SpaRepository spas;
    private final RoomRepository rooms;
    private final jakarta.persistence.EntityManager em;
    private final com.nirvana.application.service.impl.SessionAllocation allocation;
    private final SlotRepository slots;
    private final ServiceRepository services;
    private final TherapistRepository therapists;
    private final BookingHoldRepository holds;
    private final BookingRepository bookings;
    private final SlotAvailabilityService availability;
    private final TherapistRoster roster;
    private final AuditTrailService audit;
    private static final List<BookingStatus> LIVE = List.of(BookingStatus.CONFIRMED,BookingStatus.PENDING_PAYMENT,BookingStatus.ARRIVED);

    public record CreateSlot(@NotNull Long serviceId,@NotNull OffsetDateTime start,@NotNull OffsetDateTime end,
                             @NotNull @Min(1) Short capacity,@NotNull Long roomId) {}
    public record ChangeSlot(OffsetDateTime start,OffsetDateTime end,@Min(1) Short capacity,Boolean closed,@Min(0) Long therapistId,Long roomId) {}
    public record SlotView(long id,long serviceId,String service,OffsetDateTime start,OffsetDateTime end,
                           int capacity,int booked,int held,int remaining,boolean closed,SlotStatus status,List<Long> bookingIds,Long therapistId,Long roomId,List<String> appointments) {}
    public record StaffDay(long therapistId,String name,boolean enabled,boolean inherited,boolean working,List<Interval> intervals) {}
    public record Day(LocalDate date,String timezone,List<SlotView> slots,List<StaffDay> therapists) {}
    public record RosterInput(@NotNull @Size(max=96) List<@NotNull @Valid Interval> intervals) {}

    private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Record not found in this spa");}
    private ResponseStatusException invalid(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private SlotView view(Slot s,ZoneId zone) {
        int held=holds.findActiveHoldUnits(s.getId(),OffsetDateTime.now());
        var ids=bookings.findBySlot_IdAndStatusIn(s.getId(),LIVE).stream().map(Booking::getId).toList();
        return view(s, zone, held, ids);
    }
    private SlotView view(Slot s, ZoneId zone, int held, List<Long> ids) {
        return new SlotView(s.getId(),s.getService().getId(),s.getService().getName(),local(s.getStartTs(),zone),local(s.getEndTs(),zone),
            s.getCapacityUnit(),s.getBookedUnits(),held,Math.max(0,s.getCapacityUnit()-s.getBookedUnits()-held),
            Boolean.TRUE.equals(s.getIsBlocked()),s.getStatus(),ids,s.getTherapist()==null?null:s.getTherapist().getId(),s.getRoom()==null?null:s.getRoom().getId(),
            jdbc.queryForList("select concat('#',b.id,' · ',coalesce(t.name,'Unassigned therapist')) from booking b left join therapist t on t.id=b.therapist_id where b.slot_id=? and b.status not in ('CANCELLED','NO_SHOW')",String.class,s.getId()));
    }
    private static OffsetDateTime local(OffsetDateTime time,ZoneId zone){return time.atZoneSameInstant(zone).toOffsetDateTime();}
    private StaffDay staff(Therapist t,List<Interval> hours,List<Interval> inheritedHours) {
        boolean enabled=Boolean.TRUE.equals(t.getIsActive()) && Boolean.TRUE.equals(t.getIsAvailable());
        boolean inherited = hours == null;
        if (inherited) {
            hours = enabled ? inheritedHours : List.of();
        }
        List<OffsetDateTime> boundaries = hours.stream().flatMap(i -> java.util.stream.Stream.of(i.start(), i.end()))
                .distinct().sorted().toList();
        boolean working = false;
        for (int i = 1; enabled && i < boundaries.size(); i++) {
            if (TherapistRoster.coversDay(hours, boundaries.get(i - 1), boundaries.get(i))) {
                working = true;
                break;
            }
        }
        return new StaffDay(t.getId(),t.getName(),enabled,inherited,working,hours);
    }

    @PostMapping("/prepare")
    public Day prepare(@PathVariable Long spaId,@RequestParam(required=false) LocalDate date) {
        return day(spaId,date);
    }

    @GetMapping
    public Day day(@PathVariable Long spaId,@RequestParam(required=false) LocalDate date) {
        ZoneId zone=TherapistRoster.zone(spas.findById(spaId).orElseThrow(this::missing));
        LocalDate day=date==null?LocalDate.now(zone):date;
        var shifts=roster.load(spaId,day,day);
        var inheritedHours = availability.openingHours(spaId,day,day,zone);
        var list=slots.findBySpa_IdAndStartTsLessThanAndEndTsGreaterThanOrderByStartTsAsc(spaId,
            day.plusDays(1).atStartOfDay(zone).toOffsetDateTime(),day.atStartOfDay(zone).toOffsetDateTime());
        var keys = list.stream().map(Slot::getId).toList();
        Map<Long,Integer> held = new HashMap<>();
        Map<Long,List<Long>> commitments = new HashMap<>();
        if (!keys.isEmpty()) {
            for (var row : holds.findActiveHoldUnitsForSlots(keys,OffsetDateTime.now()))
                held.put((Long)row[0],((Number)row[1]).intValue());
            for (var row : bookings.commitmentIds(keys,LIVE))
                commitments.computeIfAbsent((Long)row[0], id -> new ArrayList<>()).add((Long)row[1]);
        }
        return new Day(day,zone.getId(),list.stream().map(s->view(s,zone,held.getOrDefault(s.getId(),0),
                commitments.getOrDefault(s.getId(),List.of()))).toList(),
            therapists.findBySpa_IdOrderByNameAsc(spaId).stream().map(t->staff(t,shifts.get(new Key(t.getId(),day)),inheritedHours)).toList());
    }

    @GetMapping("/available-therapists")
    public List<SlotAvailabilityResponse> lookup(@PathVariable Long spaId,@RequestParam Long serviceId,
            @RequestParam(required=false) Long slotId,@RequestParam(required=false) OffsetDateTime start,
            @RequestParam(required=false) LocalDate date) {
        if ((slotId==null)==(start==null)) throw invalid("Supply exactly one slotId or start");
        ZoneId zone=TherapistRoster.zone(spas.findById(spaId).orElseThrow(this::missing));
        services.findByIdAndSpa_Id(serviceId,spaId).orElseThrow(this::missing);
        if(slotId!=null){
            Slot slot=slots.findById(slotId).orElseThrow(this::missing);
            if(!slot.getSpa().getId().equals(spaId)||!slot.getService().getId().equals(serviceId))throw missing();
            start=slot.getStartTs();
        }
        final OffsetDateTime when=start;
        LocalDate localDay=local(when,zone).toLocalDate();
        if(date!=null && !localDay.equals(date))throw invalid("Date must match the appointment in spa local time");
        if(when.isBefore(OffsetDateTime.now()))return List.of();
        return availability.getTherapistAvailabilityForServiceOnDate(spaId,serviceId,localDay).stream()
            .filter(s->s.startTs().isEqual(when) && (slotId==null || slotId.equals(s.slotId()))).toList();
    }

    public record Candidate(long id,String name) {}
    @GetMapping("/slots/{slotId}/therapists")
    public List<Candidate> candidates(@PathVariable Long spaId,@PathVariable Long slotId) {
        Slot slot=slots.findById(slotId).filter(s->s.getSpa().getId().equals(spaId)).orElseThrow(this::missing);
        return allocation.eligible(slot,null,null).stream()
                .map(t->new Candidate(t.getId(),t.getName())).toList();
    }

    public record RoomView(long id,String name,boolean active){}
    public record RoomInput(@NotBlank @Size(max=128) String name,boolean active){}
    public record Generate(@NotNull Long serviceId,@NotEmpty Set<@NotNull Long> roomIds,@NotNull LocalDate date,
                           @NotNull LocalTime start,@NotNull LocalTime end,@Min(1) @Max(1440) Integer minutes){}
    public record Skip(long roomId,OffsetDateTime start,OffsetDateTime end){}
    public record Generated(int created,List<Skip> skipped){}
    @GetMapping("/rooms")
    public List<RoomView> rooms(@PathVariable Long spaId){
        return rooms.findBySpa_IdOrderByName(spaId).stream().map(r->new RoomView(r.getId(),r.getName(),r.isActive())).toList();
    }
    private SpaRoom activeRoom(long spa,long room){
        return rooms.findByIdAndSpa_Id(room,spa).filter(SpaRoom::isActive).orElseThrow(this::missing);
    }
    private boolean overlaps(long room,OffsetDateTime start,OffsetDateTime end,long exclude){
        return em.createQuery("select count(s) from Slot s where s.room.id=:room and s.startTs<:end and s.endTs>:start and s.id<>:exclude",Long.class)
                .setParameter("room",room).setParameter("start",start).setParameter("end",end).setParameter("exclude",exclude).getSingleResult()>0;
    }
    private void requireRoomFree(Slot s){
        if(s.getRoom()==null)throw invalid("Select a room");
        if(overlaps(s.getRoom().getId(),s.getStartTs(),s.getEndTs(),s.getId()==null?-1:s.getId()))
            throw new IllegalStateException("This room already has a slot in that interval");
    }
    @PostMapping("/rooms")
    public RoomView addRoom(@PathVariable Long spaId,@Valid @RequestBody RoomInput in){
        SpaRoom r=new SpaRoom();r.setSpa(spas.lockSchedule(spaId).orElseThrow(this::missing));r.setName(in.name().trim());
        r.setActive(in.active());rooms.save(r);return new RoomView(r.getId(),r.getName(),r.isActive());
    }
    @PutMapping("/rooms/{id}")
    public RoomView updateRoom(@PathVariable Long spaId,@PathVariable Long id,@Valid @RequestBody RoomInput in){
        spas.lockSchedule(spaId).orElseThrow(this::missing);
        SpaRoom r=rooms.findByIdAndSpa_Id(id,spaId).orElseThrow(this::missing);
        if(!in.active()){
            var ids=em.createQuery("select b.id from Booking b where b.room.id=:id and b.endTs>:now and b.status in ('ARRIVED','CONFIRMED','PENDING_PAYMENT')",Long.class)
                .setParameter("id",id).setParameter("now",OffsetDateTime.now()).getResultList();
            if(!ids.isEmpty())throw new IllegalStateException("Resolve appointments "+ids+" first");
            if(em.createQuery("select count(h) from BookingHold h where h.room.id=:id and h.expiresAt>:now and h.convertedToBooking=false",Long.class).setParameter("id",id).setParameter("now",OffsetDateTime.now()).getSingleResult()>0)
                throw new IllegalStateException("Wait for active holds to expire");
        }
        r.setActive(in.active());r.setName(in.name().trim());return new RoomView(id,r.getName(),r.isActive());
    }
    @PostMapping("/generate")
    public Generated generate(@PathVariable Long spaId,@Valid @RequestBody Generate in){
        var spa=spas.lockSchedule(spaId).orElseThrow(this::missing);
        var service=services.findByIdAndSpa_Id(in.serviceId(),spaId).orElseThrow(this::missing);
        int duration=in.minutes()==null?60:in.minutes();
        if(duration<service.getDurationMin() || !in.end().isAfter(in.start()))throw invalid("Choose a window and duration covering the treatment");
        ZoneId zone=TherapistRoster.zone(spa);
        if(in.date().isBefore(LocalDate.now(zone)))throw invalid("Choose today or a future date");
        var until=in.date().atTime(in.end()).atZone(zone).toOffsetDateTime();
        int created=0;List<Skip> skipped=new ArrayList<>();
        for(long id:in.roomIds()){
            var room=activeRoom(spaId,id);
            for(var at=in.date().atTime(in.start()).atZone(zone).toOffsetDateTime();!at.plusMinutes(duration).isAfter(until);at=at.plusMinutes(duration)){
                var end=at.plusMinutes(duration);
                if(overlaps(id,at,end,-1) || jdbc.update("insert ignore into generated_room_slot values (?,?)",id,java.sql.Timestamp.from(at.toInstant()))==0){
                    skipped.add(new Skip(id,at,end));continue;
                }
                Slot slot=new Slot();slot.setRoom(room);slot.setSpa(spa);slot.setService(service);slot.setStartTs(at);slot.setEndTs(end);
                slot.setCapacityUnit(service.getMaxPersons().shortValue());slots.save(slot);created++;
            }
        }
        return new Generated(created,skipped);
    }
    @DeleteMapping("/slots/{id}")
    public void deleteSlot(@PathVariable Long spaId,@PathVariable Long id){
        spas.lockSchedule(spaId).orElseThrow(this::missing);
        Slot slot=slots.findByIdForUpdate(id).filter(s->s.getSpa().getId().equals(spaId)).orElseThrow(this::missing);
        var ids=jdbc.queryForList("select id from booking where slot_id=?",Long.class,id);
        if(!ids.isEmpty() || slot.getBookedUnits()>0)throw new IllegalStateException("Keep slot for appointments "+ids);
        if(holds.findActiveHoldUnits(id,OffsetDateTime.now())>0)throw new IllegalStateException("Wait for active holds to expire");
        jdbc.update("delete from booking_hold where slot_id=? and converted_to_booking=0 and expires_at<=UTC_TIMESTAMP()",id);
        em.createQuery("update WaitlistEntry w set w.slot=null where w.slot.id=:id").setParameter("id",id).executeUpdate();
        slots.delete(slot);
    }
    @GetMapping("/therapists/{id}/week")
    public List<TherapistRoster.Shift> week(@PathVariable Long spaId,@PathVariable Long id){
        therapists.findByIdAndSpa_Id(id,spaId).orElseThrow(this::missing);return roster.weekly(id);
    }
    @PutMapping("/therapists/{id}/week")
    public void week(@PathVariable Long spaId,@PathVariable Long id,@RequestBody List<TherapistRoster.Shift> shifts){
        var spa=spas.lockSchedule(spaId).orElseThrow(this::missing);
        therapists.findByIdAndSpa_Id(id,spaId).orElseThrow(this::missing);
        if(shifts==null || shifts.size()>42 || shifts.stream().anyMatch(s->s==null || s.weekday()<1 || s.weekday()>7 || s.start()==null || s.end()==null || s.start().equals(s.end())))throw invalid("Invalid weekly shift");
        roster.saveWeek(id,shifts);
        validateHeldRoster(spa,id);
        for(var row:em.createQuery("select b.id,b.startTs,b.endTs from Booking b where b.therapist.id=:id and b.endTs>:now and b.status in ('ARRIVED','CONFIRMED','PENDING_PAYMENT')",Object[].class)
                .setParameter("id",id).setParameter("now",OffsetDateTime.now()).getResultList()){
            var start=(OffsetDateTime)row[1];var end=(OffsetDateTime)row[2];
            var zone=TherapistRoster.zone(spa);
            if(!TherapistRoster.covers(roster.load(spaId,local(start,zone).toLocalDate(),local(end,zone).toLocalDate()),id,zone,start,end))
                throw new IllegalStateException("Resolve appointment #"+row[0]+" before changing the weekly roster");
        }
    }
    private void validateHeldRoster(Spa spa,long therapist){
        var active=em.createQuery("select h.slot from BookingHold h where h.therapist.id=:id and h.expiresAt>:now and h.convertedToBooking=false",Slot.class)
                .setParameter("id",therapist).setParameter("now",OffsetDateTime.now()).getResultList();
        for(var slot:active)if(!roster.permits(slot,therapist))
            throw new IllegalStateException("Wait for the reservation at "+local(slot.getStartTs(),TherapistRoster.zone(spa))+" to expire");
    }
    private void checkTimes(Slot slot) {
        if(slot.getStartTs().isBefore(OffsetDateTime.now()) ||
           slot.getEndTs().isBefore(slot.getStartTs().plusMinutes(slot.getService().getDurationMin())))
            throw invalid("Choose a future start and allow the full service duration");
    }
    @PostMapping("/slots")
    public SlotView create(@PathVariable Long spaId,@Valid @RequestBody CreateSlot in) {
        Spa spa=spas.lockSchedule(spaId).orElseThrow(this::missing);
        Slot slot=new Slot();
        slot.setSpa(spa);slot.setService(services.findByIdAndSpa_Id(in.serviceId(),spaId).orElseThrow(this::missing));
        slot.setRoom(activeRoom(spaId,in.roomId()));
        slot.setStartTs(in.start());slot.setEndTs(in.end());slot.setCapacityUnit(in.capacity());
        requireRoomFree(slot);
        checkTimes(slot);
        slots.saveAndFlush(slot);
        SlotView after=view(slot,TherapistRoster.zone(spa));
        audit.record(AdminActionType.CREATE,"slot",slot.getId(),SecurityUtils.getCurrentUserId(),null,after,"Owner created slot");
        return after;
    }

    @PatchMapping("/slots/{slotId}")
    public SlotView change(@PathVariable Long spaId,@PathVariable Long slotId,@Valid @RequestBody ChangeSlot in) {
        Spa spa=spas.lockSchedule(spaId).orElseThrow(this::missing);
        Slot slot=slots.findByIdForUpdate(slotId).orElseThrow(this::missing);
        if(!spaId.equals(slot.getSpa().getId()))throw missing();
        ZoneId zone=TherapistRoster.zone(spa);
        SlotView before=view(slot,zone);
        boolean moving=(in.start()!=null && !in.start().isEqual(slot.getStartTs())) || (in.end()!=null && !in.end().isEqual(slot.getEndTs()));
        boolean changingRoom=in.roomId()!=null && !Objects.equals(in.roomId(),before.roomId());
        if ((changingRoom || moving || Boolean.TRUE.equals(in.closed())) && !before.bookingIds().isEmpty())
            throw new IllegalStateException("Reschedule or cancel appointments "+before.bookingIds()+" first");
        if(changingRoom && before.held()+before.booked()>0) throw new IllegalStateException("Room has existing commitments");
        if(changingRoom)slot.setRoom(activeRoom(spaId,in.roomId()));
        if(changingRoom || moving){
            var history=jdbc.queryForList("select id from booking where slot_id=?",Long.class,slotId);
            if(!history.isEmpty()) throw new IllegalStateException("Keep original slot for appointments "+history+"; create a new slot instead");
        }
        if(moving && (before.booked()+before.held()>0 || !before.bookingIds().isEmpty()))
            throw new IllegalStateException("Release holds and reschedule existing bookings before moving this slot");
        if(Boolean.TRUE.equals(in.closed()) && before.held()>0)throw new IllegalStateException("Wait for active holds to expire before closing");
        if(in.capacity()!=null){
            if(in.capacity()<before.booked()+before.held())throw new IllegalStateException("Capacity must cover booked and held units");
            slot.setCapacityUnit(in.capacity());
            if(slot.getStatus()==SlotStatus.BOOKED && in.capacity()>before.booked())slot.setStatus(SlotStatus.OPEN);
        }
        if(in.start()!=null)slot.setStartTs(in.start());
        if(in.end()!=null)slot.setEndTs(in.end());
        if(moving)checkTimes(slot);
        if(moving || changingRoom)requireRoomFree(slot);
        if (in.therapistId()!=null) {
            var current = before.therapistId();
            Long requested = in.therapistId()==0 ? null : in.therapistId();
            if (!Objects.equals(current,requested)) {
                if (before.held()>0 || before.booked()>0 || !before.bookingIds().isEmpty())
                    throw new IllegalStateException("Resolve existing bookings and holds before changing the slot therapist");
                slot.setTherapist(requested==null ? null : therapists.findByIdAndSpa_Id(requested,spaId).orElseThrow(this::missing));
            }
        }
        if (slot.getTherapist()!=null && (moving || !Objects.equals(before.therapistId(),slot.getTherapist().getId()))) {
            Long tid=slot.getTherapist().getId();
            if (therapists.findActiveAvailableForSpaAndService(spaId,slot.getService().getId()).stream().noneMatch(t->t.getId().equals(tid))
                    || !roster.permits(slot,tid) || bookings.existsActiveTherapistConflict(tid,slot.getStartTs(),TherapistRoster.end(slot)))
                throw new IllegalStateException("Choose a qualified therapist working and free throughout this slot");
        }
        if(in.closed()!=null)slot.setIsBlocked(in.closed());
        SlotView after=view(slot,zone);
        audit.record(AdminActionType.UPDATE,"slot",slotId,SecurityUtils.getCurrentUserId(),before,after,"Owner edited slot");
        return after;
    }

    @PutMapping("/therapists/{therapistId}/days/{day}")
    public StaffDay replaceDay(@PathVariable Long spaId,@PathVariable Long therapistId,@PathVariable LocalDate day,
                               @Valid @RequestBody RosterInput in) {
        Spa spa=spas.lockSchedule(spaId).orElseThrow(this::missing);
        Therapist t=therapists.findByIdAndSpa_Id(therapistId,spaId).orElseThrow(this::missing);
        ZoneId zone=TherapistRoster.zone(spa);
        var from=day.atStartOfDay(zone).toOffsetDateTime();
        var to=day.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        List<Interval> hours=new ArrayList<>();
        for(Interval i:in.intervals()){
            if(!i.start().isBefore(i.end()) || i.start().isBefore(from) || i.end().isAfter(to))
                throw invalid("Intervals must fit this spa-local day; split overnight shifts at midnight");
            hours.add(new Interval(i.kind(),local(i.start(),zone),local(i.end(),zone)));
        }
        var key=new Key(therapistId,day);
        var current=roster.load(spaId,day,day);
        StaffDay before=staff(t,current.put(key,hours),availability.openingHours(spaId,day,day,zone));
        for(Object[] b:em.createQuery("select b.id,b.startTs,b.endTs from Booking b where b.therapist.id=:id and b.startTs<:end and b.endTs>:start and b.status in ('ARRIVED','CONFIRMED','PENDING_PAYMENT')",Object[].class)
                .setParameter("id",therapistId).setParameter("start",from).setParameter("end",to).getResultList()){
            OffsetDateTime start=(OffsetDateTime)b[1], end=(OffsetDateTime)b[2];
            if(!TherapistRoster.covers(current,therapistId,zone,start.isBefore(from)?from:start,end.isAfter(to)?to:end))
                throw new IllegalStateException("Reschedule appointment #"+b[0]+" at "+local(start,zone)+" before changing this roster");
        }
        roster.save(therapistId,day,hours);
        validateHeldRoster(spa,therapistId);
        StaffDay after=staff(t,hours,List.of());
        audit.record(AdminActionType.UPDATE,"therapist_roster",therapistId,SecurityUtils.getCurrentUserId(),before,after,"Roster for "+day);
        return after;
    }
}
