package com.nirvana.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
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

    public boolean configured() { return apiKey != null && !apiKey.isBlank(); }

    private JsonNode call(String path, HttpMethod method, Object body, String fields) {
        if (!configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Set GOOGLE_PLACES_API_KEY on the backend and enable Places API (New) to search cities.");
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
                "id,displayName,formattedAddress,internationalPhoneNumber,websiteUri,googleMapsUri,attributions");
    }
}
