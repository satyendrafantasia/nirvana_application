package com.nirvana.application.controller;

import com.nirvana.application.model.dto.SpaOnboardingSummaryResponse;
import com.nirvana.application.service.GooglePlaceOnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/google-places/spas")
@RequiredArgsConstructor
public class AdminGooglePlaceOnboardingController {

    private final GooglePlaceOnboardingService googlePlaceOnboardingService;

    @PostMapping("/{spaId}/approve")
    public SpaOnboardingSummaryResponse approveImportedSpa(@PathVariable Long spaId) {
        return googlePlaceOnboardingService.approveImportedSpa(spaId);
    }
}
