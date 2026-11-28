package com.nirvana.application.service.impl;

import com.nirvana.application.model.*;
import com.nirvana.application.repository.TherapistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

/** Writers call under the spa scheduling lock. Unknown legacy resources reserve the whole overlap. */
@Component @RequiredArgsConstructor
public class SessionAllocation {
    private final jakarta.persistence.EntityManager em;
    private final TherapistRepository therapists;
    private final TherapistRoster roster;
    public static final String TAKEN="This time is no longer available";
    public List<Therapist> eligible(Slot slot, Long ownHold, Long ownBooking) {
        return eligible(slot,ownHold,ownBooking,0);
    }
    /** extraMinutes: add-on time after the slot, for which the therapist and room must also be free. */
    public List<Therapist> eligible(Slot slot, Long ownHold, Long ownBooking, int extraMinutes) {
        return snapshot(slot.getSpa(),slot.getService().getId(),slot.getStartTs(),slot.getEndTs().plusMinutes(Math.max(0,extraMinutes)),ownHold,ownBooking)
            .eligible(slot,extraMinutes);
    }

    private record Occupied(Long room,Long therapist,java.time.OffsetDateTime start,java.time.OffsetDateTime end) {}
    public record Snapshot(List<Therapist> team, java.time.ZoneId zone, Map<TherapistRoster.Key,List<TherapistRoster.Interval>> shifts,
                           List<Occupied> occupied) {
        public List<Therapist> eligible(Slot slot){
            return eligible(slot,0);
        }
        public List<Therapist> eligible(Slot slot,int extraMinutes){
            var room=slot.getRoom();
            if(room==null || !room.isActive() || !room.getSpa().getId().equals(slot.getSpa().getId())
                    || slot.getEndTs().isBefore(slot.getStartTs().plusMinutes(slot.getService().getDurationMin()))) return List.of();
            var end=slot.getEndTs().plusMinutes(Math.max(0,extraMinutes));
            Set<Long> busy=new HashSet<>();
            for(var o:occupied){
                if(!o.start.isBefore(end) || !o.end.isAfter(slot.getStartTs()))continue;
                if(o.room==null || o.therapist==null || o.room.equals(room.getId()))return List.of();
                busy.add(o.therapist);
            }
            return team.stream().filter(t->slot.allowsTherapist(t.getId()) && !busy.contains(t.getId())
                    && TherapistRoster.covers(shifts,t.getId(),zone,slot.getStartTs(),end)).toList();
        }
    }
    public Snapshot snapshot(Spa spa,long service,java.time.OffsetDateTime from,java.time.OffsetDateTime until,Long ownHold,Long ownBooking){
        var rows=new ArrayList<>(em.createQuery("""
            select b.room.id,b.therapist.id,b.startTs,b.endTs from Booking b where b.spa.id=:spa and b.id<>:exclude
            and b.status in ('PENDING_PAYMENT','CONFIRMED','ARRIVED') and b.startTs<:until and b.endTs>:from
            """,Object[].class).setParameter("spa",spa.getId()).setParameter("exclude",ownBooking==null?-1L:ownBooking)
                .setParameter("from",from).setParameter("until",until).getResultList());
        // A hold also keeps its add-on minutes after the slot.
        for(var h:em.createQuery("""
            select h.room.id,h.therapist.id,h.slot.startTs,h.slot.endTs,h.extraMinutes from BookingHold h where h.spa.id=:spa and h.id<>:exclude and h.convertedToBooking=false
            and h.expiresAt>:now and h.slot.startTs<:until and h.slot.endTs>:earliest
            """,Object[].class).setParameter("spa",spa.getId()).setParameter("exclude",ownHold==null?-1L:ownHold)
            .setParameter("now",java.time.OffsetDateTime.now()).setParameter("earliest",from.minusMinutes(com.nirvana.application.service.addons.BookingAddOns.MAX_EXTRA_MINUTES))
            .setParameter("until",until).getResultList()){
            var end=((java.time.OffsetDateTime)h[3]).plusMinutes(((Number)h[4]).intValue());
            if(end.isAfter(from))rows.add(new Object[]{h[0],h[1],h[2],end});
        }
        var zone=TherapistRoster.zone(spa);
        return new Snapshot(therapists.findActiveAvailableForSpaAndService(spa.getId(),service).stream().sorted(Comparator.comparing(Therapist::getId)).toList(),
            zone,roster.load(spa.getId(),from.atZoneSameInstant(zone).toLocalDate(),until.atZoneSameInstant(zone).toLocalDate()),
            rows.stream().map(r->new Occupied((Long)r[0],(Long)r[1],(java.time.OffsetDateTime)r[2],(java.time.OffsetDateTime)r[3])).toList());
    }
    public static String preference(Long id,String label){
        return id!=null?"NAMED":label!=null && !label.isBlank()?"LABEL":"ANY";
    }
    public static String customerDisplay(Spa spa,Therapist t){
        if(t==null)return null;
        String name=Boolean.TRUE.equals(spa.getAllowTherapistSelection())?t.getPublicName():null;
        String label=Boolean.TRUE.equals(spa.getAllowTherapistTypeSelection())?t.getType():null;
        if(name==null || name.isBlank())return label;
        return label==null || label.isBlank()?name:name+" · "+label;
    }
    public void validatePreference(Spa spa,Long id,String label){
        if(label!=null && !label.isBlank() && !Boolean.TRUE.equals(spa.getAllowTherapistTypeSelection()))
            throw new IllegalStateException(TAKEN+"; label selection is disabled");
        if(id!=null && (!Boolean.TRUE.equals(spa.getAllowTherapistSelection()) ||
                therapists.findByIdAndSpa_Id(id,spa.getId()).filter(t->t.getPublicName()!=null && !t.getPublicName().isBlank()).isEmpty()))
            throw new IllegalStateException(TAKEN+"; choose a published therapist");
    }
    public Therapist choose(Slot slot,Long therapist,String type,Long hold,Long booking){
        return choose(slot,therapist,type,hold,booking,0);
    }
    public Therapist choose(Slot slot,Long therapist,String type,Long hold,Long booking,int extraMinutes){
        return eligible(slot,hold,booking,extraMinutes).stream().filter(t->therapist==null || therapist.equals(t.getId()))
                .filter(t->type==null || type.isBlank() || type.equalsIgnoreCase(t.getType()))
                .findFirst().orElseThrow(()->new IllegalStateException(TAKEN));
    }
}
