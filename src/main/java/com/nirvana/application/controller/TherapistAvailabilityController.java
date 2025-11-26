package com.nirvana.application.controller;

import com.nirvana.application.model.dto.SlotAvailabilityResponse;
import com.nirvana.application.service.impl.SlotAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/spas/{spaId}/services/{serviceId}/therapists")
@RequiredArgsConstructor
public class TherapistAvailabilityController {

    private final SlotAvailabilityService slotAvailabilityService;

    @GetMapping("/availability")
    public List<SlotAvailabilityResponse> getTherapistAvailability(
            @PathVariable Long spaId,
            @PathVariable Long serviceId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return slotAvailabilityService.getTherapistAvailabilityForServiceOnDate(spaId, serviceId, date);
    }
}
