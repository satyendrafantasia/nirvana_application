package com.nirvana.application.controller;

import com.nirvana.application.model.dto.ProviderAvailabilitySyncRequest;
import com.nirvana.application.model.dto.SlotAvailabilityResponse;
import com.nirvana.application.model.dto.SlotHoldRequest;
import com.nirvana.application.model.dto.SlotHoldResponse;
import com.nirvana.application.model.dto.WeekSlotsResponse;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.BookingHoldService;
import com.nirvana.application.service.impl.SlotAvailabilityService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/spas/{spaId}/services/{serviceId}/slots")
@RequiredArgsConstructor
@Validated
public class SlotController {

    private final SlotAvailabilityService slotAvailabilityService;
    private final BookingHoldService bookingHoldService;
    private final com.nirvana.application.service.addons.BookingAddOns addOns;

    @GetMapping("/week")
    public WeekSlotsResponse getWeekSlots(
            @PathVariable Long spaId,
            @PathVariable Long serviceId,
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "days", defaultValue = "7") @Min(1) @Max(14) int days,
            @RequestParam(name = "guests", defaultValue = "1") @Min(1) int guests,
            @RequestParam(name = "addOnIds", required = false) List<Long> addOnIds
    ) {
        LocalDate startDate = date != null ? date : LocalDate.now();
        int extra = addOns.select(spaId, serviceId, addOnIds, guests).extraMinutes();
        return slotAvailabilityService.getAvailableSlotsForServiceRange(spaId, serviceId, startDate, days, guests, extra);
    }

    @GetMapping
    public List<SlotAvailabilityResponse> getDaySlots(
            @PathVariable Long spaId,
            @PathVariable Long serviceId,
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "guests", defaultValue = "1") @Min(1) int guests,
            @RequestParam(name = "addOnIds", required = false) List<Long> addOnIds,
            // Moving a booking that has add-ons: the minutes it was sold with (the server checks again on the move).
            @RequestParam(name = "extraMinutes", defaultValue = "0") @Min(0) @Max(com.nirvana.application.service.addons.BookingAddOns.MAX_EXTRA_MINUTES) int extraMinutes
    ) {
        int extra = Math.max(extraMinutes, addOns.select(spaId, serviceId, addOnIds, guests).extraMinutes());
        return slotAvailabilityService.getAvailableSlotsForServiceOnDate(spaId, serviceId, date, guests, extra);
    }

    @PostMapping("/{slotId}/hold")
    public SlotHoldResponse holdSlot(
            @PathVariable Long spaId,
            @PathVariable Long serviceId,
            @PathVariable Long slotId,
            @Valid @RequestBody SlotHoldRequest request
    ) {
        request.setSpaId(spaId);
        request.setServiceId(serviceId);
        request.setSlotId(slotId);
        Long userId = SecurityUtils.getCurrentUserId();
        return bookingHoldService.holdSlot(userId, request);
    }

    @org.springframework.security.access.prepost.PreAuthorize("@staffAccess.spa(#spaId)")
    @PostMapping("/{slotId}/sync")
    public SlotAvailabilityResponse syncAvailability(
            @PathVariable Long spaId,
            @PathVariable Long serviceId,
            @PathVariable Long slotId,
            @Valid @RequestBody ProviderAvailabilitySyncRequest request
    ) {
        return slotAvailabilityService.syncFromProvider(spaId, serviceId, slotId, request);
    }
}
