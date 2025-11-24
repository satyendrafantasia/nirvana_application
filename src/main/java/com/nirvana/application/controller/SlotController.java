// src/main/java/com/nirvana/application/controller/SlotController.java
package com.nirvana.application.controller;

import com.nirvana.application.model.dto.WeekSlotsResponse;
import com.nirvana.application.service.impl.SlotAvailabilityService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/spas/{spaId}/services/{serviceId}")
@RequiredArgsConstructor
@Validated
public class SlotController {

    private final SlotAvailabilityService slotAvailabilityService;

    /**
     * Fresha-style week view:
     *
     * GET /api/spas/{spaId}/services/{serviceId}/slots/week?startDate=2025-11-23&days=7&guests=1
     *
     * - startDate: first day of the strip (default = today in spa timezone)
     * - days: length of strip (1–14, default 7)
     * - guests: number of guests (default 1)
     */
    @GetMapping("/slots/week")
    public WeekSlotsResponse getWeekSlots(
            @PathVariable Long spaId,
            @PathVariable Long serviceId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam(defaultValue = "7") @Min(1) @Max(14) int days,
            @RequestParam(defaultValue = "1") @Min(1) int guests
    ) {
        LocalDate effectiveStart = (startDate != null) ? startDate : LocalDate.now();
        return slotAvailabilityService.getAvailableSlotsForServiceRange(
                spaId,
                serviceId,
                effectiveStart,
                days,
                guests
        );
    }
}
