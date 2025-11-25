package com.nirvana.application.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayRepairConfig {

    @Bean
    public FlywayMigrationStrategy repairAndMigrateStrategy() {
        return flyway -> repairAndMigrate(flyway);
    }

    private void repairAndMigrate(Flyway flyway) {
        flyway.repair();
        flyway.migrate();
    }
}
