package com.nirvana.application.controller;

import com.nirvana.application.model.dto.OnboardingDraftRequest;
import com.nirvana.application.model.dto.OnboardingDraftResponse;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.OnboardingDraftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/onboarding/spas/drafts")
@RequiredArgsConstructor
public class OnboardingDraftController {

    private final OnboardingDraftService onboardingDraftService;

    @PostMapping
    public OnboardingDraftResponse saveDraft(@Valid @RequestBody OnboardingDraftRequest request) {
        Long ownerUserId = SecurityUtils.getCurrentUserId();
        return onboardingDraftService.saveDraft(ownerUserId, request);
    }

    @GetMapping
    public List<OnboardingDraftResponse> listDrafts() {
        Long ownerUserId = SecurityUtils.getCurrentUserId();
        return onboardingDraftService.listDrafts(ownerUserId);
    }

    @GetMapping("/{resumeToken}")
    public OnboardingDraftResponse getDraft(@PathVariable String resumeToken) {
        Long ownerUserId = SecurityUtils.getCurrentUserId();
        return onboardingDraftService.getDraft(ownerUserId, resumeToken);
    }

    @PostMapping("/{resumeToken}/heartbeat")
    public OnboardingDraftResponse heartbeat(@PathVariable String resumeToken) {
        Long ownerUserId = SecurityUtils.getCurrentUserId();
        return onboardingDraftService.heartbeat(ownerUserId, resumeToken);
    }

    @DeleteMapping("/{resumeToken}")
    public void deleteDraft(@PathVariable String resumeToken) {
        Long ownerUserId = SecurityUtils.getCurrentUserId();
        onboardingDraftService.deleteDraft(ownerUserId, resumeToken);
    }
}
