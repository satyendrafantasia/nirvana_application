// src/main/java/com/nirvana/application/service/SlotAvailabilityService.java
package com.nirvana.application.service.impl;

import com.nirvana.application.model.*;
import com.nirvana.application.model.dto.DaySlotsResponse;
import com.nirvana.application.model.dto.ProviderAvailabilitySyncRequest;
import com.nirvana.application.model.dto.SlotAvailabilityResponse;
import com.nirvana.application.model.dto.TherapistAvailabilityResponse;
import com.nirvana.application.model.dto.TherapistTypeAvailabilityResponse;
import com.nirvana.application.model.dto.WeekSlotsResponse;
import com.nirvana.application.model.enums.SlotStatus;
import com.nirvana.application.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;


@Service
@Slf4j
@RequiredArgsConstructor
public class SlotAvailabilityService {

    private final TherapistRoster roster;
    private final SessionAllocation allocation;
    private final SpaRepository spaRepository;
    private final ServiceRepository serviceRepository;
    private final SlotRepository slotRepository;
    private final ScheduleRuleRepository scheduleRuleRepository;
    private final ClosureRepository closureRepository;
    private final BookingHoldRepository bookingHoldRepository;
    private final TherapistRepository therapistRepository;
    private final BookingRepository bookingRepository;

    // ---------- SINGLE DAY (you already had this flow) ----------

    @Transactional(readOnly = true)
    public List<SlotAvailabilityResponse> getAvailableSlotsForServiceOnDate(
            Long spaId,
            Long serviceId,
            LocalDate date,
            int guests
    ) {
        WeekSlotsResponse week = getAvailableSlotsForServiceRange(spaId, serviceId, date, 1, guests);
        return week.days().isEmpty() ? List.of() : week.days().get(0).slots();
    }

    // ---------- WEEK / RANGE VIEW (for Fresha-style UI) ----------

    @Transactional(readOnly = true)
    public WeekSlotsResponse getAvailableSlotsForServiceRange(
            Long spaId,
            Long serviceId,
            LocalDate startDate,
            int days,
            int guests
    ) {
        return getAvailableSlotsForServiceRange(spaId, serviceId, startDate, days, guests, false);
    }

    @Transactional(readOnly = true)
    public List<SlotAvailabilityResponse> getTherapistAvailabilityForServiceOnDate(
            Long spaId,
            Long serviceId,
            LocalDate date
    ) {
        WeekSlotsResponse week = getAvailableSlotsForServiceRange(spaId, serviceId, date, 1, 1, true);
        return week.days().isEmpty() ? List.of() : week.days().get(0).slots();
    }

    private WeekSlotsResponse getAvailableSlotsForServiceRange(
            Long spaId,
            Long serviceId,
            LocalDate startDate,
            int days,
            int guests,
            boolean forceTherapistVisibility
    ) {
        if (guests <= 0) {
            throw new IllegalArgumentException("guests must be > 0");
        }
        if (days <= 0 || days > 14) { // hard-limit, don’t let FE abuse it
            throw new IllegalArgumentException("days must be between 1 and 14");
        }

        Spa spa = spaRepository.findById(spaId)
                .orElseThrow(() -> new EntityNotFoundException("Spa not found: " + spaId));

        com.nirvana.application.model.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new EntityNotFoundException("Service not found: " + serviceId));

        if (!service.getSpa().getId().equals(spa.getId())) {
            throw new IllegalArgumentException("Service does not belong to given spa");
        }
        if (Boolean.FALSE.equals(spa.getIsActive())) {
            throw new IllegalStateException("Spa is inactive");
        }
        if (Boolean.FALSE.equals(spa.getIsVerified())) {
            throw new IllegalStateException("Spa is not verified");
        }
        if (Boolean.FALSE.equals(service.getIsActive())) {
            throw new IllegalStateException("Service is inactive");
        }

        String tzId = resolveTimezone(spa);
        ZoneId zoneId = ZoneId.of(tzId);

        ZonedDateTime startOfRange = startDate.atStartOfDay(zoneId);
        ZonedDateTime endOfRange = startOfRange.plusDays(days);

        OffsetDateTime from = startOfRange.toOffsetDateTime();
        OffsetDateTime to = endOfRange.toOffsetDateTime();

        // Fetch all slots for spa+service in range
        List<Slot> slots = slotRepository.findSlotsForServiceBetween(spaId, serviceId, from, to);
        Map<Long, Integer> holdMap = buildHoldMap(slots);

        boolean therapistSelectionEnabled = forceTherapistVisibility || Boolean.TRUE.equals(spa.getAllowTherapistSelection());
        boolean therapistTypeSelectionEnabled = forceTherapistVisibility || Boolean.TRUE.equals(spa.getAllowTherapistTypeSelection());

        OffsetDateTime latest = slots.stream().map(TherapistRoster::end).max(Comparator.naturalOrder()).orElse(to);
        var resources=allocation.snapshot(spa,serviceId,from,latest,null,null);

        // Fetch all closures overlapping the whole range
        List<Closure> closures = closureRepository.findClosuresOverlapping(spaId, from, latest);

        var openingHours = openingHours(spaId, startDate, latest.atZoneSameInstant(zoneId).toLocalDate(), zoneId);

        Map<Long,List<Therapist>> eligible=new HashMap<>();
        // Group slots per local date and filter using schedule + closure + capacity rules
        Map<LocalDate, List<SlotAvailabilityResponse>> byDate = slots.stream()
                .map(slot -> Map.entry(
                        slot,
                        slot.getStartTs().atZoneSameInstant(zoneId).toLocalDate()
                ))
                .filter(entry -> {
                    LocalDate d = entry.getValue();
                    return !d.isBefore(startDate) && !d.isAfter(startDate.plusDays(days - 1));
                })
                .filter(entry -> {
                    Slot slot = entry.getKey();
                    var team=resources.eligible(slot);
                    eligible.put(slot.getId(),team);
                    if(team.isEmpty()) return false;
                    return isSlotBookable(slot, openingHours, closures, guests, holdMap.getOrDefault(slot.getId(), 0));
                })
                .map(entry -> Map.entry(entry.getValue(), toDto(
                        entry.getKey(),
                        guests,
                        holdMap.getOrDefault(entry.getKey().getId(), 0),
                        therapistSelectionEnabled,
                        therapistTypeSelectionEnabled,
                        therapistSelectionEnabled ? buildTherapistAvailability(eligible.get(entry.getKey().getId())) : List.of(),
                        therapistTypeSelectionEnabled ? buildTherapistTypeAvailability(eligible.get(entry.getKey().getId())) : List.of()
                )))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));

        // Build ordered DaySlotsResponse list
        List<DaySlotsResponse> daysList = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = startDate.plusDays(i);
            List<SlotAvailabilityResponse> daySlots = byDate.getOrDefault(d, List.of())
                    .stream()
                    .sorted(Comparator.comparing(SlotAvailabilityResponse::startTs))
                    .toList();
            daysList.add(new DaySlotsResponse(d, daySlots));
        }

        return new WeekSlotsResponse(spaId, serviceId, daysList);
    }

    // ---------- internal helpers ----------

    /** Operating intervals clipped to the requested local dates, including overnight carry-in. */
    public List<TherapistRoster.Interval> openingHours(long spaId, LocalDate first, LocalDate last, ZoneId zone) {
        var from = first.atStartOfDay(zone).toOffsetDateTime();
        var until = last.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        List<TherapistRoster.Interval> hours = new ArrayList<>();
        Spa fallback = spaRepository.findById(spaId).orElseThrow();
        for (LocalDate d = first.minusDays(1); !d.isAfter(last); d = d.plusDays(1)) {
            List<ScheduleRule> rules;
            if (scheduleRuleRepository.hasScheduleOn(spaId,d)) {
                rules = scheduleRuleRepository.findApplicableRulesForDay(spaId,(short)d.getDayOfWeek().getValue(),d);
            } else {
                if (!fallback.getWorkingDays().isEmpty() && !fallback.getWorkingDays().contains(d.getDayOfWeek())) continue;
                ScheduleRule rule = new ScheduleRule();
                rule.setOpenLocal(Optional.ofNullable(fallback.getOpenTimeLocal()).orElse(LocalTime.of(10,0)));
                rule.setCloseLocal(Optional.ofNullable(fallback.getCloseTimeLocal()).orElse(LocalTime.of(21,0)));
                rules = List.of(rule);
            }
            for (var rule : rules) {
                if (Boolean.TRUE.equals(rule.getIsHoliday())) continue;
                var start = d.atTime(rule.getOpenLocal()).atZone(zone).toOffsetDateTime();
                var endDate = rule.getCloseLocal().isAfter(rule.getOpenLocal()) ? d : d.plusDays(1);
                var end = endDate.atTime(rule.getCloseLocal()).atZone(zone).toOffsetDateTime();
                if (end.isAfter(from) && start.isBefore(until)) {
                    hours.add(new TherapistRoster.Interval(TherapistRoster.Kind.WORK,
                            start.isBefore(from) ? from : start, end.isAfter(until) ? until : end));
                }
            }
        }
        return hours;
    }

    public void requireBookable(Slot slot,int guests,int heldUnits) {
        ZoneId zone=TherapistRoster.zone(slot.getSpa());
        LocalDate day=slot.getStartTs().atZoneSameInstant(zone).toLocalDate();
        long spa=slot.getSpa().getId();
        var hours=openingHours(spa,day,TherapistRoster.end(slot).atZoneSameInstant(zone).toLocalDate(),zone);
        var closures=closureRepository.findClosuresOverlapping(spa,slot.getStartTs(),TherapistRoster.end(slot));
        if(!isSlotBookable(slot,hours,closures,guests,heldUnits))
            throw new IllegalStateException(SessionAllocation.TAKEN);
    }

    private boolean isSlotBookable(
            Slot slot,
            List<TherapistRoster.Interval> openingHours,
            List<Closure> closures,
            int guests,
            int heldUnits
    ) {
        if (slot.getStartTs().isBefore(OffsetDateTime.now()) || Boolean.TRUE.equals(slot.getIsBlocked())) {
            return false;
        }
        if (slot.getStatus() != SlotStatus.OPEN) {
            return false;
        }

        short capacity = slot.getCapacityUnit();
        short booked = slot.getBookedUnits();
        short remaining = (short) Math.max(0, capacity - booked - heldUnits);
        if (remaining < guests) {
            return false;
        }

        if (!TherapistRoster.coversDay(openingHours, slot.getStartTs(), TherapistRoster.end(slot))) {
            return false;
        }

        for (Closure c : closures) {
            if (overlaps(slot, c)) {
                return false;
            }
        }
        return true;
    }

    private boolean overlaps(Slot slot, Closure closure) {
        return slot.getStartTs().isBefore(closure.getEndTs()) &&
                TherapistRoster.end(slot).isAfter(closure.getStartTs());
    }

    private String resolveTimezone(Spa spa) {
        return TherapistRoster.zone(spa).getId();
    }

    private Map<Long, Integer> buildHoldMap(List<Slot> slots) {
        if (slots.isEmpty()) {
            return Map.of();
        }
        List<Long> slotIds = slots.stream().map(Slot::getId).toList();
        List<Object[]> raw = bookingHoldRepository.findActiveHoldUnitsForSlots(slotIds, OffsetDateTime.now(ZoneOffset.UTC));
        Map<Long, Integer> result = new HashMap<>();
        for (Object[] row : raw) {
            result.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return result;
    }

    private List<TherapistAvailabilityResponse> buildTherapistAvailability(List<Therapist> therapists){
        return therapists.stream()
                .map(therapist -> new TherapistAvailabilityResponse(
                        therapist.getId(),
                        therapist.getName(),
                        therapist.getDisplayName(),
                        therapist.getProfileImageUrl(),
                        therapist.getType(),
                        true
                ))
                .toList();
    }

    private List<TherapistTypeAvailabilityResponse> buildTherapistTypeAvailability(List<Therapist> therapists) {
        Map<String,Integer> availableByType=new HashMap<>();
        therapists.stream().filter(t->t.getType()!=null && !t.getType().isBlank())
                .forEach(therapist -> availableByType.merge(therapist.getType(), 1, Integer::sum));

        return availableByType.entrySet().stream()
                .map(entry -> new TherapistTypeAvailabilityResponse(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(TherapistTypeAvailabilityResponse::type))
                .toList();
    }

    private SlotAvailabilityResponse toDto(
            Slot slot,
            int guests,
            int heldUnits,
            boolean therapistSelectionEnabled,
            boolean therapistTypeSelectionEnabled,
            List<TherapistAvailabilityResponse> therapists,
            List<TherapistTypeAvailabilityResponse> therapistTypes
    ) {
        short capacity = slot.getCapacityUnit();
        short booked = slot.getBookedUnits();
        int remaining = Math.max(0, capacity - booked - heldUnits);

        String uiStatus;
        if (slot.getStartTs().isBefore(OffsetDateTime.now()) || Boolean.TRUE.equals(slot.getIsBlocked())) {
            uiStatus = "BLOCKED";
        } else if (remaining < guests) {
            uiStatus = "FULL";
        } else {
            uiStatus = "AVAILABLE";
        }

        return new SlotAvailabilityResponse(
                slot.getId(),
                slot.getStartTs(),
                slot.getEndTs(),
                remaining,
                slot.getRoomNumber(),
                uiStatus,
                therapistSelectionEnabled,
                therapistTypeSelectionEnabled,
                therapistSelectionEnabled ? therapists : List.of(),
                therapistTypeSelectionEnabled ? therapistTypes : List.of()
        );
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public SlotAvailabilityResponse syncFromProvider(Long spaId, Long serviceId, Long slotId, ProviderAvailabilitySyncRequest request) {
        spaRepository.lockSchedule(spaId).orElseThrow(() -> new EntityNotFoundException("Spa not found"));
        Slot slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new EntityNotFoundException("Slot not found: " + slotId));
        if (!slot.getSpa().getId().equals(spaId) || !slot.getService().getId().equals(serviceId)) {
            throw new IllegalArgumentException("Slot does not belong to spa");
        }

        int held=bookingHoldRepository.findActiveHoldUnits(slotId,OffsetDateTime.now());
        int booked=request.getBookedUnits()==null?slot.getBookedUnits():request.getBookedUnits();
        int capacity=request.getCapacityUnit()==null?slot.getCapacityUnit():request.getCapacityUnit();
        int assigned=bookingRepository.findBySlot_IdAndStatusIn(slotId,List.of(
            com.nirvana.application.model.enums.BookingStatus.CONFIRMED,com.nirvana.application.model.enums.BookingStatus.PENDING_PAYMENT,
            com.nirvana.application.model.enums.BookingStatus.ARRIVED)).stream().mapToInt(Booking::getGuestCount).sum();
        if (booked<assigned || capacity<booked+held || (held>0 && request.getStatus()!=SlotStatus.OPEN))
            throw new IllegalStateException("Provider update conflicts with existing bookings or holds");
        if (request.getCapacityUnit() != null && request.getCapacityUnit() > 0) {
            slot.setCapacityUnit(request.getCapacityUnit());
        }
        if (request.getBookedUnits() != null && request.getBookedUnits() >= 0) {
            slot.setBookedUnits(request.getBookedUnits());
        }
        slot.setStatus(request.getStatus());
        slot.setHoldExpiresTs(request.getProviderHoldExpiresAt());

        slotRepository.save(slot);
        boolean therapistSelectionEnabled = Boolean.TRUE.equals(slot.getSpa().getAllowTherapistSelection());
        return toDto(slot, 1, held, therapistSelectionEnabled, true, List.of(), List.of());
    }
}
