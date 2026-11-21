package com.nirvana.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Adds appointment times each day for spas onboarded from leads, so they stay bookable for their
 * whole booking window instead of running out after the days set up at onboarding. Safe to run on
 * several instances at once: each time is claimed before it is added.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LeadSlotTopUpJob {
    private final LeadOnboardingService onboarding;

    @Scheduled(initialDelayString = "${leads.slot-top-up.initial-delay-ms:120000}",
            fixedDelayString = "${leads.slot-top-up.interval-ms:21600000}")
    public void topUp() {
        long added = onboarding.topUpSlots();
        if (added > 0) log.info("Added {} appointment times for spas onboarded from leads", added);
    }
}
