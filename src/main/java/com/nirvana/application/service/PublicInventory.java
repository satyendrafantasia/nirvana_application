package com.nirvana.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Public releases unpublish explicitly fictional inventory while keeping its history. */
@Slf4j
@Component
public class PublicInventory implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final boolean hideFixtures;

    public PublicInventory(JdbcTemplate jdbc, @Value("${seo.public-origin:}") String publicOrigin,
                           @Value("${inventory.hide-fixtures:}") String override) {
        this.jdbc = jdbc;
        this.hideFixtures = override.isBlank() ? !publicOrigin.isBlank() : Boolean.parseBoolean(override);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!hideFixtures) return;
        int hidden = jdbc.update("""
                UPDATE spa SET is_active=0, is_verified=0, is_featured=0, version=version+1
                WHERE (is_active=1 OR is_verified=1 OR is_featured=1) AND (
                    JSON_UNQUOTE(JSON_EXTRACT(meta, '$.fictional')) IN ('true','1')
                    OR JSON_EXTRACT(meta, '$.demoBatch') IS NOT NULL
                    OR JSON_CONTAINS(tags, JSON_QUOTE('demo'))=1
                    OR website_url='https://nirvana.test' OR website_url LIKE 'https://nirvana.test/spa/%'
                    OR name='Nirvana Owner Preview')""");
        if (hidden > 0) log.info("Unpublished {} demo or preview spa listings", hidden);
    }
}
