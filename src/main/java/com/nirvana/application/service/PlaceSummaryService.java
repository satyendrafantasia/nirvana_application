package com.nirvana.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Turns a Google Places search response into de-duplicated summaries marked with bookable Nirvana spas. */
@Service
@RequiredArgsConstructor
public class PlaceSummaryService {
    private final JdbcTemplate jdbc;

    /** Summaries keyed by Place ID, in provider order, each with at most one photo. */
    public Map<String, ObjectNode> summarize(JsonNode response) {
        Map<String, ObjectNode> unique = new LinkedHashMap<>();
        response.path("places").forEach(place -> {
            String id = place.path("id").asText();
            if (place instanceof ObjectNode object && id.matches("[A-Za-z0-9_-]{1,255}")
                    && SpaPlaceFilter.isDiscoverySpa(place)) {
                ObjectNode summary = object.deepCopy();
                JsonNode photos = summary.remove("photos");
                if (photos != null && photos.isArray() && !photos.isEmpty()) {
                    summary.putArray("photos").add(photos.get(0));
                }
                unique.putIfAbsent(id, summary);
            }
        });
        if (!unique.isEmpty()) {
            String slots = String.join(",", Collections.nCopies(unique.size(), "?"));
            // Return only approved, active catalogue matches. Never expose drafts or owner data.
            var matches = jdbc.queryForList("SELECT s.id, s.google_place_id AS place_id FROM spa s WHERE s.is_active=true AND s.is_verified=true AND s.booking_mode='INSTANT' AND s.google_place_id IN (" + slots + ") ORDER BY s.id", unique.keySet().toArray());
            for (var match : matches) {
                ObjectNode place = unique.get(String.valueOf(match.get("place_id")));
                if (place != null && !place.has("nirvanaSpaId") && "OPERATIONAL".equals(place.path("businessStatus").asText()))
                    place.put("nirvanaSpaId", ((Number) match.get("id")).longValue());
            }
        }
        return unique;
    }

    /** Marks one place's details with the bookable Nirvana spa it belongs to, if any. */
    public JsonNode withBookableSpa(JsonNode place) {
        if (place instanceof ObjectNode object && "OPERATIONAL".equals(place.path("businessStatus").asText())) {
            var ids = jdbc.queryForList("SELECT s.id FROM spa s WHERE s.is_active=true AND s.is_verified=true AND s.booking_mode='INSTANT' AND s.google_place_id=? ORDER BY s.id LIMIT 1",
                    Long.class, place.path("id").asText());
            if (!ids.isEmpty()) object.put("nirvanaSpaId", ids.get(0));
        }
        return place;
    }
}
