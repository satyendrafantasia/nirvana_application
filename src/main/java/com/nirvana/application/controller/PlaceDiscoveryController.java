package com.nirvana.application.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nirvana.application.model.dto.PlaceDiscoveryRequest;
import com.nirvana.application.service.CityPlacesClient;
import com.nirvana.application.service.PlaceReferenceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import com.nirvana.application.service.PlaceSummaryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/discovery")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class PlaceDiscoveryController {
    private final CityPlacesClient places;
    private final PlaceSummaryService summaries;
    private final PlaceReferenceService references;
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
        Map<String,ObjectNode> unique = summaries.summarize(response, request.mode());
        references.record(unique.keySet(), request.mode().name(), request.city(), request.locality(), request.countryCode());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
                "places", unique.values(), "nextPageToken", response.path("nextPageToken").asText("")));
    }
    @GetMapping("/places/{placeId}")
    public ResponseEntity<JsonNode> details(@PathVariable String placeId) {
        return live(summaries.withBookableSpa(places.details(placeId)));
    }
    @GetMapping("/places/{placeId}/photos/{photoId}")
    public ResponseEntity<JsonNode> photo(@PathVariable String placeId, @PathVariable String photoId) {
        return live(places.photo(placeId, photoId));
    }
    @GetMapping("/places/{placeId}/photos/{photoId}/image")
    public ResponseEntity<byte[]> photoImage(@PathVariable String placeId, @PathVariable String photoId) {
        return places.photoImage(placeId, photoId);
    }
    private ResponseEntity<JsonNode> live(JsonNode body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
