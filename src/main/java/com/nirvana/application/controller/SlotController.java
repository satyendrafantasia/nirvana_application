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

    @GetMapping("/week")
    public WeekSlotsResponse getWeekSlots(
            @PathVariable Long spaId,
            @PathVariable Long serviceId,
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "days", defaultValue = "7") @Min(1) @Max(14) int days,
            @RequestParam(name = "guests", defaultValue = "1") @Min(1) int guests
    ) {
        LocalDate startDate = date != null ? date : LocalDate.now();
        return slotAvailabilityService.getAvailableSlotsForServiceRange(spaId, serviceId, startDate, days, guests);
    }

    @GetMapping
    public List<SlotAvailabilityResponse> getDaySlots(
            @PathVariable Long spaId,
            @PathVariable Long serviceId,
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "guests", defaultValue = "1") @Min(1) int guests
    ) {
        return slotAvailabilityService.getAvailableSlotsForServiceOnDate(spaId, serviceId, date, guests);
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

    @PostMapping("/{slotId}/sync")
    public SlotAvailabilityResponse syncAvailability(
            @PathVariable Long spaId,
            @PathVariable Long serviceId,
            @PathVariable Long slotId,
            @Valid @RequestBody ProviderAvailabilitySyncRequest request
    ) {
        // serviceId is kept in the path for parity with other routes; SlotAvailabilityService will validate spa
        return slotAvailabilityService.syncFromProvider(spaId, slotId, request);
    }
}
