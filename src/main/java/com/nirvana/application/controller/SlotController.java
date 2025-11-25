package com.nirvana.application.controller;

import com.nirvana.application.model.dto.SlotAvailabilityResponse;
import com.nirvana.application.model.dto.WeekSlotsResponse;
import com.nirvana.application.service.impl.SlotAvailabilityService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/spas/{spaId}/services/{serviceId}/slots")
@RequiredArgsConstructor
@Validated
public class SlotController {

    private final SlotAvailabilityService slotAvailabilityService;

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
}
