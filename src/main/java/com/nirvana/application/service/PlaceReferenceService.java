package com.nirvana.application.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaceReferenceService {
    private final JdbcTemplate jdbc;

    // Persist only provider IDs and our search metadata, never Google business content.
    @Transactional
    public void record(Collection<String> ids, String mode, String city, String locality, String country) {
        String normalizedCity = normalize(city);
        String normalizedCountry = country.toUpperCase(Locale.ROOT);
        for (String id : new LinkedHashSet<>(ids)) {
            if (id == null || !id.matches("[A-Za-z0-9_-]{1,255}")) continue;
            jdbc.update("""
                INSERT INTO place_discovery_reference
                    (place_id,search_mode,search_city,search_locality,country_code)
                VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE
                    search_mode=VALUES(search_mode),search_city=VALUES(search_city),
                    search_locality=VALUES(search_locality),country_code=VALUES(country_code),
                    last_seen_at=CURRENT_TIMESTAMP,search_count=search_count+1
                """, id, mode, normalizedCity, normalize(locality), normalizedCountry);
            if (normalizedCity != null) {
                jdbc.update("INSERT INTO city_place_discovery (city,country_code,place_id) VALUES (?,?,?) ON DUPLICATE KEY UPDATE id=id",
                    normalizedCity, normalizedCountry, id);
            }
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
