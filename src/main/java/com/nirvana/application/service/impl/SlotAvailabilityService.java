// src/main/java/com/nirvana/application/service/SlotAvailabilityService.java
package com.nirvana.application.service.impl;

import com.nirvana.application.model.*;
import com.nirvana.application.model.dto.DaySlotsResponse;
import com.nirvana.application.model.dto.ProviderAvailabilitySyncRequest;
import com.nirvana.application.model.dto.SlotAvailabilityResponse;
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

    private final SpaRepository spaRepository;
    private final ServiceRepository serviceRepository;
    private final SlotRepository slotRepository;
    private final ScheduleRuleRepository scheduleRuleRepository;
    private final ClosureRepository closureRepository;
    private final BookingHoldRepository bookingHoldRepository;

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

        // Fetch all closures overlapping the whole range
        List<Closure> closures = closureRepository.findClosuresOverlapping(spaId, from, to);

        // Pre-group schedule rules by date to avoid hitting DB per slot
        Map<LocalDate, List<ScheduleRule>> rulesByDate = new HashMap<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            short weekday = (short) date.getDayOfWeek().getValue(); // 1=Mon..7=Sun
            List<ScheduleRule> rules =
                    scheduleRuleRepository.findApplicableRulesForDay(spaId, weekday, date);
            rulesByDate.put(date, rules);
        }

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
                    LocalDate d = entry.getValue();
                    List<ScheduleRule> rules = rulesByDate.getOrDefault(d, List.of());
                    return isSlotBookable(slot, rules, closures, zoneId, guests, holdMap.getOrDefault(slot.getId(), 0));
                })
                .map(entry -> Map.entry(entry.getValue(), toDto(entry.getKey(), guests, holdMap.getOrDefault(entry.getKey().getId(), 0))))
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

    private boolean isSlotBookable(
            Slot slot,
            List<ScheduleRule> rulesForDay,
            List<Closure> closures,
            ZoneId zoneId,
            int guests,
            int heldUnits
    ) {
        if (Boolean.TRUE.equals(slot.getIsBlocked())) {
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

        ZonedDateTime slotStartLocal = slot.getStartTs().atZoneSameInstant(zoneId);
        LocalTime slotStartTime = slotStartLocal.toLocalTime();

        boolean matchesSchedule = rulesForDay.stream()
                .filter(rule -> !Boolean.TRUE.equals(rule.getIsHoliday()))
                .anyMatch(rule ->
                        !slotStartTime.isBefore(rule.getOpenLocal()) &&
                                !slotStartTime.isAfter(rule.getCloseLocal())
                );

        if (!matchesSchedule) {
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
                slot.getEndTs().isAfter(closure.getStartTs());
    }

    private String resolveTimezone(Spa spa) {
        Address a = spa.getAddress();
        if (a != null && a.getTimezone() != null && !a.getTimezone().isBlank()) {
            return a.getTimezone();
        }
        if (spa.getTimezone() != null && !spa.getTimezone().isBlank()) {
            return spa.getTimezone();
        }
        return ZoneId.systemDefault().getId();
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

    private SlotAvailabilityResponse toDto(Slot slot, int guests, int heldUnits) {
        short capacity = slot.getCapacityUnit();
        short booked = slot.getBookedUnits();
        int remaining = Math.max(0, capacity - booked - heldUnits);

        String uiStatus;
        if (Boolean.TRUE.equals(slot.getIsBlocked())) {
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
                uiStatus
        );
    }

    @Transactional
    public SlotAvailabilityResponse syncFromProvider(Long spaId, Long slotId, ProviderAvailabilitySyncRequest request) {
        Slot slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new EntityNotFoundException("Slot not found: " + slotId));
        if (!slot.getSpa().getId().equals(spaId)) {
            throw new IllegalArgumentException("Slot does not belong to spa");
        }

        if (request.getCapacityUnit() != null && request.getCapacityUnit() > 0) {
            slot.setCapacityUnit(request.getCapacityUnit());
        }
        if (request.getBookedUnits() != null && request.getBookedUnits() >= 0) {
            slot.setBookedUnits(request.getBookedUnits());
        }
        slot.setStatus(request.getStatus());
        slot.setHoldExpiresTs(request.getProviderHoldExpiresAt());

        slotRepository.save(slot);
        return toDto(slot, 1, 0);
    }
}
