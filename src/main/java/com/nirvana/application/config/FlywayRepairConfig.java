package com.nirvana.application.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayRepairConfig {

    @org.springframework.beans.factory.annotation.Value("${app.flyway.repair-on-migrate:false}")
    private boolean repairOnMigrate;

    @Bean
    public FlywayMigrationStrategy repairAndMigrateStrategy() {
        return flyway -> repairAndMigrate(flyway);
    }

    private void repairAndMigrate(Flyway flyway) {
        // Repair silently accepts edited or missing migrations, so it is opt-in.
        if (repairOnMigrate) {
            flyway.repair();
        }
        flyway.migrate();
    }
}
