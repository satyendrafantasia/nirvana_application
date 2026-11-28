package com.nirvana.application.groups;

import com.nirvana.application.model.Closure;
import com.nirvana.application.model.Slot;
import com.nirvana.application.model.Spa;
import com.nirvana.application.repository.BookingHoldRepository;
import com.nirvana.application.repository.ClosureRepository;
import com.nirvana.application.repository.ServiceRepository;
import com.nirvana.application.repository.SlotRepository;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.service.impl.SessionAllocation;
import com.nirvana.application.service.impl.SlotAvailabilityService;
import com.nirvana.application.service.impl.TherapistRoster;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Which start times fit a whole group, and which slot and therapist each guest gets.
 * A time is offered only when every guest can be seated at once (see {@link GroupPlanner}).
 */
@Service
@RequiredArgsConstructor
public class GroupAvailability {
    public static final int MAX_GUESTS = 6;

    private final SpaRepository spas;
    private final ServiceRepository services;
    private final SlotRepository slots;
    private final BookingHoldRepository holds;
    private final ClosureRepository closures;
    private final SlotAvailabilityService availability;
    private final SessionAllocation allocation;
    private final JdbcTemplate jdbc;

    public record GuestRequest(Long serviceId, Long therapistId, String therapistType) {}

    public record GroupTime(OffsetDateTime startTs, OffsetDateTime endTs) {}

    /** The seats chosen for one start time, in guest order, with the locked-in slot rows. */
    public record Plan(List<GroupPlanner.Seat> seats, Map<Long, Slot> slots) {
        public Slot slot(int guest) { return slots.get(seats.get(guest).slotId()); }
    }

    @Transactional
    public List<GroupTime> times(long spaId, LocalDate date, List<GuestRequest> guests) {
        Spa spa = spas.findById(spaId).orElseThrow(() -> new EntityNotFoundException("Spa not found"));
        validate(spa, guests);
        ZoneId zone = TherapistRoster.zone(spa);
        OffsetDateTime from = date.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime to = date.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        for (Long service : distinctServices(guests)) availability.prepareStandardSlots(spa, date, 1, service);
        var candidates = load(spa, guests, from, to);
        List<GroupTime> result = new ArrayList<>();
        for (Instant start : candidates.commonStarts()) {
            var guestOptions = candidates.guestsAt(start, guests, null, null);
            if (guestOptions == null) continue;
            var seats = GroupPlanner.plan(guestOptions);
            if (seats == null) continue;
            OffsetDateTime end = seats.stream().map(s -> TherapistRoster.end(candidates.byId.get(s.slotId())))
                    .max(Comparator.naturalOrder()).orElseThrow();
            result.add(new GroupTime(OffsetDateTime.ofInstant(start, zone), end.atZoneSameInstant(zone).toOffsetDateTime()));
        }
        return result;
    }

    /**
     * Seats the group at {@code start}. Callers hold the spa scheduling lock, so the answer stays true
     * until their transaction ends. Throws {@link SessionAllocation#TAKEN} when the group does not fit.
     */
    public Plan plan(Spa spa, OffsetDateTime start, List<GuestRequest> guests, List<Long> fixedSlots, List<Long> fixedTherapists) {
        validate(spa, guests);
        var candidates = load(spa, guests, start, start.plusSeconds(1));
        var guestOptions = candidates.guestsAt(start.toInstant(), guests, fixedSlots, fixedTherapists);
        var seats = guestOptions == null ? null : GroupPlanner.plan(guestOptions);
        if (seats == null) throw new IllegalStateException(SessionAllocation.TAKEN + " for your whole group");
        Map<Long, Slot> locked = new HashMap<>();
        for (var seat : seats) {
            locked.computeIfAbsent(seat.slotId(), id -> slots.findByIdForUpdate(id).orElseThrow());
        }
        return new Plan(seats, locked);
    }

    public void validate(Spa spa, List<GuestRequest> guests) {
        if (guests == null || guests.size() < 2 || guests.size() > MAX_GUESTS)
            throw new IllegalArgumentException("A group booking is for 2 to " + MAX_GUESTS + " guests");
        if (Boolean.FALSE.equals(spa.getIsActive()) || Boolean.FALSE.equals(spa.getIsVerified()))
            throw new IllegalStateException("This spa is not taking bookings");
        Set<Long> named = new HashSet<>();
        for (var guest : guests) {
            if (guest == null || guest.serviceId() == null) throw new IllegalArgumentException("Choose a service for every guest");
            var service = services.findById(guest.serviceId()).orElseThrow(() -> new EntityNotFoundException("Service not found"));
            if (!service.getSpa().getId().equals(spa.getId())) throw new IllegalArgumentException("Service does not belong to Spa");
            if (Boolean.FALSE.equals(service.getIsActive())) throw new IllegalStateException("Service is inactive");
            if (guest.therapistId() != null && !named.add(guest.therapistId()))
                throw new IllegalArgumentException("Each guest needs a different therapist");
            allocation.validatePreference(spa, guest.therapistId(), guest.therapistType());
        }
    }

    /** The shared-room capacity of each service, or no entry when guests need their own rooms. */
    public Map<Long, Integer> sideBySide(Collection<Long> serviceIds) {
        Map<Long, Integer> result = new HashMap<>();
        for (Long id : serviceIds) {
            Integer capacity = jdbc.query("SELECT side_by_side_capacity FROM service WHERE id=?",
                    rs -> rs.next() ? (Integer) rs.getObject(1, Integer.class) : null, id);
            if (capacity != null && capacity > 1) result.put(id, capacity);
        }
        return result;
    }

    public List<Long> serviceIds(long spaId) {
        return jdbc.queryForList("SELECT id FROM service WHERE spa_id=? AND is_active=1", Long.class, spaId);
    }

    private static List<Long> distinctServices(List<GuestRequest> guests) {
        return guests.stream().map(GuestRequest::serviceId).distinct().toList();
    }

    private Candidates load(Spa spa, List<GuestRequest> guests, OffsetDateTime from, OffsetDateTime to) {
        var serviceIds = distinctServices(guests);
        Map<Long, List<Slot>> byService = new HashMap<>();
        Map<Long, Slot> byId = new HashMap<>();
        for (Long service : serviceIds) {
            var found = slots.findSlotsForServiceBetween(spa.getId(), service, from, to);
            byService.put(service, found);
            found.forEach(s -> byId.put(s.getId(), s));
        }
        Map<Long, Integer> held = new HashMap<>();
        if (!byId.isEmpty()) {
            for (Object[] row : holds.findActiveHoldUnitsForSlots(byId.keySet(), OffsetDateTime.now(ZoneOffset.UTC)))
                held.put((Long) row[0], ((Number) row[1]).intValue());
        }
        OffsetDateTime latest = byId.values().stream().map(TherapistRoster::end).max(Comparator.naturalOrder()).orElse(to);
        ZoneId zone = TherapistRoster.zone(spa);
        var hours = availability.openingHours(spa.getId(), from.atZoneSameInstant(zone).toLocalDate(),
                latest.atZoneSameInstant(zone).toLocalDate(), zone);
        List<Closure> closed = closures.findClosuresOverlapping(spa.getId(), from, latest);
        Map<Long, SessionAllocation.Snapshot> teams = new HashMap<>();
        for (Long service : serviceIds) teams.put(service, allocation.snapshot(spa, service, from, latest, null, null));
        return new Candidates(byService, byId, held, hours, closed, teams, sideBySide(serviceIds));
    }

    private record Candidates(Map<Long, List<Slot>> byService, Map<Long, Slot> byId, Map<Long, Integer> held,
                              List<TherapistRoster.Interval> hours, List<Closure> closed,
                              Map<Long, SessionAllocation.Snapshot> teams, Map<Long, Integer> sideBySide) {

        /** Start times every requested service has at least one slot for. */
        SortedSet<Instant> commonStarts() {
            SortedSet<Instant> common = null;
            for (var list : byService.values()) {
                SortedSet<Instant> starts = new TreeSet<>();
                list.forEach(s -> starts.add(s.getStartTs().toInstant()));
                if (common == null) common = starts; else common.retainAll(starts);
            }
            return common == null ? new TreeSet<>() : common;
        }

        List<GroupPlanner.Guest> guestsAt(Instant start, List<GuestRequest> guests, List<Long> fixedSlots, List<Long> fixedTherapists) {
            List<GroupPlanner.Guest> result = new ArrayList<>();
            Map<Long, List<GroupPlanner.Option>> cache = new HashMap<>();
            for (int i = 0; i < guests.size(); i++) {
                var guest = guests.get(i);
                var options = cache.computeIfAbsent(guest.serviceId(), service -> options(service, start));
                if (options.isEmpty()) return null;
                Long pinned = fixedTherapists == null || fixedTherapists.get(i) == null ? guest.therapistId() : fixedTherapists.get(i);
                result.add(new GroupPlanner.Guest(guest.serviceId(), sideBySide.get(guest.serviceId()), pinned,
                        guest.therapistType(), fixedSlots == null ? null : fixedSlots.get(i), options));
            }
            return result;
        }

        private List<GroupPlanner.Option> options(long service, Instant start) {
            List<GroupPlanner.Option> result = new ArrayList<>();
            for (Slot slot : byService.getOrDefault(service, List.of())) {
                if (!slot.getStartTs().toInstant().equals(start) || !bookable(slot) || slot.getRoom() == null) continue;
                var staff = teams.get(service).eligible(slot).stream()
                        .map(t -> new GroupPlanner.Staff(t.getId(), t.getType())).toList();
                if (staff.isEmpty()) continue;
                result.add(new GroupPlanner.Option(slot.getId(), slot.getRoom().getId(), slot.getRoom().getCapacity(), staff));
            }
            return result;
        }

        /** Same rules as a single booking: future, open, not held, inside opening hours, no closure. */
        private boolean bookable(Slot slot) {
            if (!slot.getStartTs().isAfter(OffsetDateTime.now()) || Boolean.TRUE.equals(slot.getIsBlocked())) return false;
            if (slot.getCapacityUnit() - slot.getBookedUnits() - held.getOrDefault(slot.getId(), 0) < 1) return false;
            var end = TherapistRoster.end(slot);
            if (!TherapistRoster.coversDay(hours, slot.getStartTs(), end)) return false;
            return closed.stream().noneMatch(c -> slot.getStartTs().isBefore(c.getEndTs()) && end.isAfter(c.getStartTs()));
        }
    }
}
