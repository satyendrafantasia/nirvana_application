package com.nirvana.application.service.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes guest health forms a configurable number of days after the visit (default 90). */
@Component
@RequiredArgsConstructor
@Slf4j
public class HealthFormRetentionJob {
    private final HealthFormService forms;
    @Value("${app.health-forms.retention-days:90}")
    private int days;

    @Scheduled(initialDelayString = "${app.health-forms.purge-initial-delay-ms:300000}",
               fixedDelayString = "${app.health-forms.purge-interval-ms:3600000}")
    public void purge() {
        int deleted = forms.purge(days);
        // Counts only: never anything from the forms themselves.
        if (deleted > 0) log.info("event=health_forms.purged count={}", deleted);
    }
}
