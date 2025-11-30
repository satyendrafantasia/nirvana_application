package com.nirvana.application.controller;

import com.nirvana.application.model.dto.GoogleBookingConfigResponse;
import com.nirvana.application.service.GoogleBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/google-booking")
@RequiredArgsConstructor
@Tag(name = "Google Booking", description = "Entry points for Google Maps Book button deep links")
public class GoogleBookingController {

    private final GoogleBookingService googleBookingService;

    @GetMapping("/resolve-spa")
    @Operation(summary = "Resolve spa for Google Maps place", description = "Resolve spa and services for a given Google Maps placeId")
    public GoogleBookingConfigResponse resolveSpa(@RequestParam String placeId,
                                                  @RequestParam(value = "fallbackSpaId", required = false) Long fallbackSpaId) {
        return googleBookingService.resolveSpaByPlaceId(placeId, fallbackSpaId);
    }

    @GetMapping("/config")
    @Operation(summary = "Bootstrap booking UI for Google Maps", description = "Return spa summary and services for Google Maps Book button entry")
    public GoogleBookingConfigResponse getConfig(@RequestParam String placeId,
                                                 @RequestParam(value = "fallbackSpaId", required = false) Long fallbackSpaId) {
        return googleBookingService.resolveSpaByPlaceId(placeId, fallbackSpaId);
    }
}
