package com.nirvana.application.service.payout;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** The weekly settlement run, and an hourly pass that sends waiting payouts and checks transfers in flight. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PayoutJob {
    private final PayoutService payouts;

    @Scheduled(cron = "${payouts.cron:0 30 3 * * MON}", zone = "UTC")
    public void settle() {
        try { payouts.run(); } catch (RuntimeException e) { log.warn("event=payout.run_failed reason={}", e.getMessage()); }
    }

    @Scheduled(initialDelayString = "${payouts.check-interval-ms:3600000}", fixedDelayString = "${payouts.check-interval-ms:3600000}")
    public void check() {
        try { payouts.sendWaiting(); } catch (RuntimeException e) { log.warn("event=payout.check_failed reason={}", e.getMessage()); }
    }
}
