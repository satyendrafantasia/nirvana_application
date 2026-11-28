package com.nirvana.application.controller;

import com.nirvana.application.model.dto.PlaceDiscoveryRequest.Mode;
import com.nirvana.application.service.CityPlacesClient;
import com.nirvana.application.service.PlaceSummaryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Nearby spas for visitors who are not signed in (the home page).
 * Each call costs money at Google, so the caller chooses nothing but the position: the radius,
 * result count and fields are fixed, there are no photos or paging, and the calls have their own
 * small budget (see CityPlacesClient) plus a tight per-address limit (see RateLimitingFilter).
 */
@RestController
@RequestMapping("/api/public/discovery")
@RequiredArgsConstructor
public class PublicDiscoveryController {
    private final CityPlacesClient places;
    private final PlaceSummaryService summaries;

    public record NearbyRequest(@NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
                                @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude) {}

    @GetMapping("/status")
    public Map<String, Boolean> status() { return Map.of("configured", places.configured()); }

    @PostMapping("/nearby")
    public ResponseEntity<Map<String, Object>> nearby(@Valid @RequestBody NearbyRequest request) {
        var found = summaries.summarize(places.publicNearby(request.latitude(), request.longitude()), Mode.NEARBY);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("places", found.values()));
    }
}
