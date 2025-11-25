package com.nirvana.application.controller;

import com.nirvana.application.model.dto.SpaOnboardingSummaryResponse;
import com.nirvana.application.service.GooglePlaceOnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/onboarding/google-places")
@RequiredArgsConstructor
public class GooglePlaceOnboardingController {

    private final GooglePlaceOnboardingService googlePlaceOnboardingService;

    @PostMapping("/{placeId}")
    public SpaOnboardingSummaryResponse onboardFromGooglePlaces(@PathVariable String placeId) {
        return googlePlaceOnboardingService.onboardFromPlaceId(placeId);
    }
}
