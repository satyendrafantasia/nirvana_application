package com.nirvana.application.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nirvana.application.model.dto.PlaceDiscoveryRequest;
import com.nirvana.application.service.CityPlacesClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/discovery")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class PlaceDiscoveryController {
    private final CityPlacesClient places;
    private final JdbcTemplate jdbc;
    public record LocationRequest(@NotBlank @Size(max=250) String query,
                                  @NotBlank @Pattern(regexp="[A-Za-z]{2}") String countryCode) {}
    @GetMapping("/status")
    public Map<String, Boolean> status() { return Map.of("configured", places.configured()); }

    @PostMapping("/locations")
    public ResponseEntity<JsonNode> locations(@Valid @RequestBody LocationRequest request) {
        return live(places.locations(request.query().strip(), request.countryCode()));
    }
    @PostMapping("/search")
    public ResponseEntity<Map<String,Object>> search(@Valid @RequestBody PlaceDiscoveryRequest request) {
        JsonNode response = places.discover(request);
        Map<String,ObjectNode> unique = new LinkedHashMap<>();
        response.path("places").forEach(place -> {
            String id = place.path("id").asText();
            if (place instanceof ObjectNode object && id.matches("[A-Za-z0-9_-]{1,255}")) unique.putIfAbsent(id, object.deepCopy());
        });
        if (!unique.isEmpty()) {
            String slots = String.join(",", Collections.nCopies(unique.size(), "?"));
            // Return only approved, active catalogue matches. Never expose drafts or owner data.
            var matches = jdbc.queryForList("SELECT s.id, s.google_place_id AS place_id FROM spa s WHERE s.is_active=true AND s.is_verified=true AND s.google_place_id IN (" + slots + ") ORDER BY s.id", unique.keySet().toArray());
            for (var match : matches) {
                ObjectNode place = unique.get(String.valueOf(match.get("place_id")));
                if (place != null && !place.has("nirvanaSpaId") && "OPERATIONAL".equals(place.path("businessStatus").asText()))
                    place.put("nirvanaSpaId", ((Number)match.get("id")).longValue());
            }
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
                "places", unique.values(), "nextPageToken", response.path("nextPageToken").asText("")));
    }
    @GetMapping("/places/{placeId}")
    public ResponseEntity<JsonNode> details(@PathVariable String placeId) { return live(places.details(placeId)); }
    private ResponseEntity<JsonNode> live(JsonNode body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
