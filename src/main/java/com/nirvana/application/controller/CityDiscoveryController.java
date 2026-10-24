package com.nirvana.application.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.nirvana.application.service.CityPlacesClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/admin/city-discovery")
@PreAuthorize("@staffAccess.admin()")
@RequiredArgsConstructor
public class CityDiscoveryController {
    private final CityPlacesClient places;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    public record SearchRequest(@NotBlank @Size(max=100) String city,
                                @NotBlank @Pattern(regexp="[A-Za-z]{2}") String countryCode,
                                @Size(max=4096) String pageToken) {}

    public record ReferencesRequest(@NotBlank @Size(max=100) String city,
            @NotBlank @Pattern(regexp="[A-Za-z]{2}") String countryCode,
            @NotEmpty @Size(max=20) List<@Pattern(regexp="[A-Za-z0-9_-]{1,255}") String> placeIds) {}
    @PostMapping("/references")
    public Map<String,Integer> saveReferences(@Valid @RequestBody ReferencesRequest request) {
        String city = request.city().strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String country = request.countryCode().toUpperCase(Locale.ROOT);
        Set<String> ids = new LinkedHashSet<>(request.placeIds());
        transactions.executeWithoutResult(tx -> {
            for (String id : ids) jdbc.update("INSERT INTO city_place_discovery (city,country_code,place_id) VALUES (?,?,?) ON DUPLICATE KEY UPDATE id=id", city, country, id);
        });
        return Map.of("processed", ids.size());
    }

    @GetMapping("/status")
    public Map<String,Boolean> status() { return Map.of("configured", places.configured()); }

    @GetMapping("/cities")
    public List<Map<String,Object>> cities() {
        return jdbc.queryForList("SELECT city, country_code AS countryCode, COUNT(*) AS places, MAX(discovered_at) AS lastImported FROM city_place_discovery GROUP BY city,country_code ORDER BY lastImported DESC LIMIT 200");
    }

    @PostMapping("/search")
    public ResponseEntity<Map<String,Object>> search(@Valid @RequestBody SearchRequest request) {
        String city = request.city().strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (city.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a city.");
        String country = request.countryCode().toUpperCase(Locale.ROOT);
        JsonNode response = places.search(city, country, request.pageToken());
        Set<String> ids = new LinkedHashSet<>();
        response.path("places").forEach(place -> {
            String id = place.path("id").asText();
            if (id.matches("[A-Za-z0-9_-]{1,255}")) ids.add(id);
        });
        transactions.executeWithoutResult(tx -> {
            for (String id : ids) jdbc.update("INSERT INTO city_place_discovery (city,country_code,place_id) VALUES (?,?,?) ON DUPLICATE KEY UPDATE id=id", city, country, id);
        });
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
                "city",city,"countryCode",country,"found",ids.size(),
                "nextPageToken",response.path("nextPageToken").asText("")));
    }

    @GetMapping("/places")
    public Map<String,Object> saved(@RequestParam String city, @RequestParam String countryCode,
                                    @RequestParam(defaultValue="0") int page) {
        if (page < 0 || page > 10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page.");
        String normalized = city.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String country = countryCode.toUpperCase(Locale.ROOT);
        var rows = jdbc.queryForList("SELECT place_id AS placeId,discovered_at AS discoveredAt FROM city_place_discovery WHERE city=? AND country_code=? ORDER BY id LIMIT 20 OFFSET ?", normalized,country,page*20);
        var total = jdbc.queryForObject("SELECT COUNT(*) FROM city_place_discovery WHERE city=? AND country_code=?",Long.class,normalized,country);
        return Map.of("content", rows,"total",total == null ? 0 : total,"page",page);
    }

    @GetMapping("/places/{placeId}")
    public ResponseEntity<JsonNode> details(@PathVariable String placeId) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM city_place_discovery WHERE place_id=?",Long.class,placeId) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Import this place through a city search first.");
        // Provider details are fetched for this view only; never persisted in the discovery table.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(places.details(placeId));
    }
}
