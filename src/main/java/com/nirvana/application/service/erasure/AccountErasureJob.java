package com.nirvana.application.service.erasure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Completes account deletions whose grace period has ended. Safe on several instances at once:
 * each account row is locked while it is erased, and an erased account is never picked again.
 * An account that picked up a blocker during the grace period (an upcoming booking, a refund in
 * progress, an open spa) is left pending and logged, and tried again on the next run.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountErasureJob {
    private final AccountErasureService erasure;

    @Scheduled(initialDelayString = "${app.account-erasure.initial-delay-ms:600000}",
            fixedDelayString = "${app.account-erasure.interval-ms:3600000}")
    public void eraseDueAccounts() {
        int erased = erasure.eraseDue();
        if (erased > 0) log.info("Erased {} accounts whose deletion grace period ended", erased);
    }
}
