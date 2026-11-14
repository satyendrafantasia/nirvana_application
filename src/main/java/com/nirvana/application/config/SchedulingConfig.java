package com.nirvana.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the @Scheduled jobs (payment reconciliation, unpaid-booking expiry, auto-completion).
 * Set app.scheduling.enabled=false on an instance that should not run background jobs.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
