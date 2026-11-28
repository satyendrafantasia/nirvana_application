package com.nirvana.application.service.recovery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Every few minutes: remind guests who left a checkout unpaid, then record which reminders led to
 * a booking. Runs only where app.scheduling.enabled is on (see SchedulingConfig). Each checkout is
 * claimed under a row lock before sending, so several instances can run it at once.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckoutRecoveryJob {
    private final CheckoutRecoveryService recovery;

    @Value("${checkout-recovery.batch-size:50}")
    private int batchSize;

    @Scheduled(initialDelayString = "${checkout-recovery.interval-ms:300000}",
            fixedDelayString = "${checkout-recovery.interval-ms:300000}")
    public void run() {
        if (!recovery.active()) return;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int sent = 0;
        for (Long bookingId : recovery.candidates(now, batchSize)) {
            try {
                var outcome = recovery.claim(bookingId, now);
                if (outcome.claim() != null && recovery.deliver(outcome.claim())) sent++;
            } catch (RuntimeException ex) {
                log.warn("Checkout reminder for booking {} failed: {}", bookingId, ex.getClass().getSimpleName());
            }
        }
        int converted = 0;
        try {
            converted = recovery.recordConversions(now);
        } catch (RuntimeException ex) {
            log.warn("Checkout reminder conversions could not be recorded: {}", ex.getClass().getSimpleName());
        }
        if (sent > 0 || converted > 0) log.info("Checkout reminders: {} sent, {} bookings recovered", sent, converted);
    }
}
