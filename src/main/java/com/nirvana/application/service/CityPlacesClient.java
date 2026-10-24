package com.nirvana.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import com.nirvana.application.model.dto.PlaceDiscoveryRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.*;

@Component
@RequiredArgsConstructor
public class CityPlacesClient {
    private final RestTemplateBuilder builder;
    @Value("${google.places.api-key:}") private String apiKey;
    @Value("${google.places.search-base-url:https://places.googleapis.com/v1}") private String baseUrl;

    @Value("${google.places.max-requests-per-minute:60}") private int maxRequestsPerMinute = 60;
    private long requestWindow;
    private int requests;
    private synchronized void reserveRequest() {
        long window = System.currentTimeMillis() / 60000;
        if (window != requestWindow) { requestWindow = window; requests = 0; }
        if (requests >= maxRequestsPerMinute) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Discovery is busy. Please try again in a minute.");
        requests++;
    }
    public boolean configured() { return apiKey != null && !apiKey.isBlank(); }

    private JsonNode call(String path, HttpMethod method, Object body, String fields) {
        if (!configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Set GOOGLE_PLACES_API_KEY on the backend and enable Places API (New) to search cities.");
        reserveRequest();
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Goog-Api-Key", apiKey);
        headers.set("X-Goog-FieldMask", fields);
        try {
            var response = builder.connectTimeout(Duration.ofSeconds(5)).readTimeout(Duration.ofSeconds(20)).build()
                    .exchange(baseUrl + path, method, new HttpEntity<>(body, headers), JsonNode.class).getBody();
            if (response == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Google Places returned an empty response.");
            return response;
        } catch (RestClientException ex) {
            // Do not expose provider response bodies, credentials or request headers.
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Google Places request failed. Check the API key, billing, quota and Places API (New) access, then retry.");
        }
    }

    public JsonNode search(String city, String country, String token) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("textQuery", "spas in " + city + ", " + country);
        body.put("includedType", "spa");
        body.put("strictTypeFiltering", true);
        body.put("pageSize", 20);
        body.put("regionCode", country);
        if (token != null && !token.isBlank()) body.put("pageToken", token);
        return call("/places:searchText", HttpMethod.POST, body, "places.id,nextPageToken");
    }

    public JsonNode details(String placeId) {
        if (!placeId.matches("[A-Za-z0-9_-]{1,255}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Place ID.");
        return call("/places/" + placeId, HttpMethod.GET, null,
                "id,displayName,formattedAddress,internationalPhoneNumber,websiteUri,googleMapsUri,rating,userRatingCount,businessStatus,attributions");
    }
    // Summary fields only. Phone/website are requested only when details are opened.
    private static final String SUMMARY = "places.id,places.displayName,places.formattedAddress,places.rating,places.userRatingCount,places.businessStatus,places.googleMapsUri,places.attributions";

    public JsonNode discover(PlaceDiscoveryRequest request) {
        if (!request.isSearchValid() || !request.isBoundsValid())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid discovery search.");
        Map<String,Object> body = new LinkedHashMap<>();
        if (request.mode() == PlaceDiscoveryRequest.Mode.NEARBY) {
            body.put("includedTypes", List.of("spa"));
            body.put("maxResultCount", 20);
            body.put("rankPreference", request.rank() == null ? "DISTANCE" : request.rank().name());
            body.put("locationRestriction", Map.of("circle", Map.of("center", Map.of(
                    "latitude", request.latitude(), "longitude", request.longitude()), "radius", request.radiusMeters())));
            return call("/places:searchNearby", HttpMethod.POST, body, SUMMARY);
        }
        String area = request.locality() == null || request.locality().isBlank() ? "" : request.locality().strip() + ", ";
        body.put("textQuery", "spas in " + area + request.city().strip() + ", " + request.countryCode().toUpperCase(Locale.ROOT));
        body.put("includedType", "spa");
        body.put("strictTypeFiltering", true);
        body.put("pageSize", 20);
        body.put("regionCode", request.countryCode().toUpperCase(Locale.ROOT));
        if (request.bounds() != null) body.put("locationRestriction", Map.of("rectangle", request.bounds()));
        if (request.pageToken() != null && !request.pageToken().isBlank()) body.put("pageToken", request.pageToken());
        return call("/places:searchText", HttpMethod.POST, body, SUMMARY + ",nextPageToken");
    }

    public JsonNode locations(String query, String country) {
        return call("/places:searchText", HttpMethod.POST,
                Map.of("textQuery", query, "regionCode", country.toUpperCase(Locale.ROOT), "pageSize", 5),
                "places.id,places.displayName,places.formattedAddress,places.location,places.viewport,places.attributions");
    }
}
