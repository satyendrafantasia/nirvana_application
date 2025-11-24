// src/main/java/com/nirvana/application/controller/SpaOnboardingController.java
package com.nirvana.application.controller;

import com.nirvana.application.model.dto.*;
import com.nirvana.application.service.SpaOnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Self-serve SPA onboarding for spa owners.
 *
 * Assumes caller is authenticated and we receive ownerUserId via header.
 * In real prod you’d pull userId from SecurityContext/JWT instead of header.
 */
@RestController
@RequestMapping("/api/onboarding/spas")
@RequiredArgsConstructor
public class SpaOnboardingController {

    private final SpaOnboardingService spaOnboardingService;

    // Start onboarding (step 1)
    @PostMapping
    public SpaOnboardingSummaryResponse startOnboarding(
            @RequestHeader("X-User-Id") Long ownerUserId,
            @Valid @RequestBody SpaOnboardingStartRequest request) {

        return spaOnboardingService.startOnboarding(ownerUserId, request);
    }

    // Update details (step 2)
    @PutMapping("/{spaId}/details")
    public SpaOnboardingSummaryResponse updateDetails(
            @RequestHeader("X-User-Id") Long ownerUserId,
            @PathVariable Long spaId,
            @Valid @RequestBody SpaOnboardingDetailsRequest request) {

        return spaOnboardingService.updateDetails(ownerUserId, spaId, request);
    }

    // Update address
    @PutMapping("/{spaId}/address")
    public SpaOnboardingSummaryResponse updateAddress(
            @RequestHeader("X-User-Id") Long ownerUserId,
            @PathVariable Long spaId,
            @Valid @RequestBody AddressDTO addressDto) {

        return spaOnboardingService.updateAddress(ownerUserId, spaId, addressDto);
    }

    // Submit KYC for review
    @PutMapping("/{spaId}/kyc")
    public SpaOnboardingSummaryResponse submitKyc(
            @RequestHeader("X-User-Id") Long ownerUserId,
            @PathVariable Long spaId,
            @Valid @RequestBody SpaKycRequest request) {

        return spaOnboardingService.submitKyc(ownerUserId, spaId, request);
    }

    // List all spas owned by this user
    @GetMapping
    public List<SpaOnboardingSummaryResponse> listMySpas(
            @RequestHeader("X-User-Id") Long ownerUserId) {

        return spaOnboardingService.listMySpas(ownerUserId);
    }

    // Get single spa
    @GetMapping("/{spaId}")
    public SpaOnboardingSummaryResponse getMySpa(
            @RequestHeader("X-User-Id") Long ownerUserId,
            @PathVariable Long spaId) {

        return spaOnboardingService.getMySpa(ownerUserId, spaId);
    }
}
