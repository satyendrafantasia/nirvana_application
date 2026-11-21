package com.nirvana.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every couple of minutes: send spas their one last reminder, then confirm what's still unanswered. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpaBookingAlertJob {
    private final SpaBookingAlerts alerts;

    @Scheduled(initialDelayString = "${spa-alerts.interval-ms:120000}", fixedDelayString = "${spa-alerts.interval-ms:120000}")
    public void run() {
        try {
            int done = alerts.runDue();
            if (done > 0) log.info("Spa booking confirmations: {} reminders sent or bookings confirmed for the spa", done);
        } catch (RuntimeException e) {
            log.warn("Spa booking confirmation run failed: {}", e.getMessage());
        }
    }
}
