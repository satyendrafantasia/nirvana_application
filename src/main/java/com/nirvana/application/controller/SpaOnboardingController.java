// src/main/java/com/nirvana/application/controller/SpaOnboardingController.java
package com.nirvana.application.controller;

import com.nirvana.application.model.dto.*;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.SpaOnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Self-serve SPA onboarding for spa owners.
 *
 * Caller identity is derived from JWT-authenticated SecurityContext.
 */
@RestController
@RequestMapping("/api/onboarding/spas")
@RequiredArgsConstructor
public class SpaOnboardingController {

    private final SpaOnboardingService spaOnboardingService;

    // Start onboarding (step 1)
    @PostMapping
    public SpaOnboardingSummaryResponse startOnboarding(
            @Valid @RequestBody SpaOnboardingStartRequest request) {

        Long ownerUserId = SecurityUtils.getCurrentUserId();
        return spaOnboardingService.startOnboarding(ownerUserId, request);
    }

    // Update details (step 2)
    @PutMapping("/{spaId}/details")
    public SpaOnboardingSummaryResponse updateDetails(
            @PathVariable Long spaId,
            @Valid @RequestBody SpaOnboardingDetailsRequest request) {

        Long ownerUserId = SecurityUtils.getCurrentUserId();
        return spaOnboardingService.updateDetails(ownerUserId, spaId, request);
    }

    // Update address
    @PutMapping("/{spaId}/address")
    public SpaOnboardingSummaryResponse updateAddress(
            @PathVariable Long spaId,
            @Valid @RequestBody AddressDTO addressDto) {

        Long ownerUserId = SecurityUtils.getCurrentUserId();
        return spaOnboardingService.updateAddress(ownerUserId, spaId, addressDto);
    }

    // Submit KYC for review
    @PutMapping("/{spaId}/kyc")
    public SpaOnboardingSummaryResponse submitKyc(
            @PathVariable Long spaId,
            @Valid @RequestBody SpaKycRequest request) {

        Long ownerUserId = SecurityUtils.getCurrentUserId();
        return spaOnboardingService.submitKyc(ownerUserId, spaId, request);
    }

    // List all spas owned by this user
    @GetMapping
    public List<SpaOnboardingSummaryResponse> listMySpas() {

        Long ownerUserId = SecurityUtils.getCurrentUserId();
        return spaOnboardingService.listMySpas(ownerUserId);
    }

    // Get single spa
    @GetMapping("/{spaId}")
    public SpaOnboardingSummaryResponse getMySpa(
            @PathVariable Long spaId) {

        Long ownerUserId = SecurityUtils.getCurrentUserId();
        return spaOnboardingService.getMySpa(ownerUserId, spaId);
    }
}
