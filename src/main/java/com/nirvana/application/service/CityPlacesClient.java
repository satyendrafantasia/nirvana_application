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
        if (fields != null) headers.set("X-Goog-FieldMask", fields);
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
        // Type and name let SpaPlaceFilter drop salons and gyms Google also tags "spa"; neither is stored.
        return call("/places:searchText", HttpMethod.POST, body, "places.id,places.primaryType,places.displayName,nextPageToken");
    }

    public JsonNode details(String placeId) {
        if (!placeId.matches("[A-Za-z0-9_-]{1,255}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Place ID.");
        // Everything the spa page shows. Google has no services or prices: those come from onboarded spas.
        return call("/places/" + placeId, HttpMethod.GET, null,
                "id,displayName,formattedAddress,internationalPhoneNumber,websiteUri,googleMapsUri,rating,userRatingCount,businessStatus,attributions,photos,reviews,"
                        + "location,editorialSummary,regularOpeningHours.weekdayDescriptions,currentOpeningHours.openNow");
    }
    /** Just the listing's photos and the credits Google requires with them, for a Nirvana spa page. */
    public JsonNode photos(String placeId) {
        if (!placeId.matches("[A-Za-z0-9_-]{1,255}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Place ID.");
        return call("/places/" + placeId, HttpMethod.GET, null, "id,displayName,googleMapsUri,attributions,photos");
    }
    public JsonNode photo(String placeId, String photoId) {
        if (!placeId.matches("[A-Za-z0-9_-]{1,255}") || !photoId.matches("[A-Za-z0-9_-]{1,4096}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid photo reference.");
        return call("/places/" + placeId + "/photos/" + photoId
                + "/media?maxWidthPx=800&skipHttpRedirect=true", HttpMethod.GET, null, null);
    }

    public ResponseEntity<byte[]> photoImage(String placeId, String photoId) {
        String url = photo(placeId, photoId).path("photoUri").asText("");
        java.net.URI uri = java.net.URI.create(url);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null
                || !uri.getHost().endsWith(".googleusercontent.com") || uri.getRawUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Google returned an unexpected photo address.");
        }
        try {
            var image = builder.connectTimeout(Duration.ofSeconds(5)).readTimeout(Duration.ofSeconds(15))
                    .redirects(org.springframework.boot.http.client.ClientHttpRequestFactorySettings.Redirects.DONT_FOLLOW)
                    .build().getForEntity(uri, byte[].class);
            MediaType type = image.getHeaders().getContentType();
            if (image.getStatusCode() != HttpStatus.OK || image.getBody() == null || type == null
                    || !Set.of("jpeg", "png", "webp", "gif", "avif").contains(type.getSubtype())
                    || !"image".equals(type.getType())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Photo unavailable.");
            }
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(type).body(image.getBody());
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Photo unavailable.");
        }
    }

    // Summary fields only. Phone/website are requested only when details are opened.
    private static final String SUMMARY = "places.id,places.primaryType,places.displayName,places.formattedAddress,places.rating,places.userRatingCount,places.businessStatus,places.googleMapsUri,places.attributions,places.photos.name,places.photos.authorAttributions";

    public JsonNode discover(PlaceDiscoveryRequest request) {
        if (!request.isSearchValid() || !request.isBoundsValid())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid discovery search.");
        Map<String,Object> body = new LinkedHashMap<>();
        if (request.mode() == PlaceDiscoveryRequest.Mode.NEARBY) {
            body.put("includedTypes", List.of("spa"));
            body.put("excludedPrimaryTypes", SpaPlaceFilter.EXCLUDED_PRIMARY_TYPES);
            body.put("maxResultCount", 20);
            body.put("rankPreference", request.rank() == null ? "DISTANCE" : request.rank().name());
            body.put("locationRestriction", Map.of("circle", Map.of("center", Map.of(
                    "latitude", request.latitude(), "longitude", request.longitude()), "radius", request.radiusMeters())));
            return call("/places:searchNearby", HttpMethod.POST, body, SUMMARY);
        }
        String area = request.locality() == null || request.locality().isBlank() ? "" : request.locality().strip() + ", ";
        String query = request.spaName() == null || request.spaName().isBlank() ? "spas" : request.spaName().strip();
        if (request.city() != null && !request.city().isBlank()) {
            query += " in " + area + request.city().strip() + ", " + request.countryCode().toUpperCase(Locale.ROOT);
        }
        body.put("textQuery", query);
        body.put("includedType", "spa");
        body.put("strictTypeFiltering", true);
        body.put("pageSize", 20);
        body.put("regionCode", request.countryCode().toUpperCase(Locale.ROOT));
        if (request.bounds() != null) body.put("locationRestriction", Map.of("rectangle", request.bounds()));
        if (request.pageToken() != null && !request.pageToken().isBlank()) body.put("pageToken", request.pageToken());
        return call("/places:searchText", HttpMethod.POST, body, SUMMARY + ",nextPageToken");
    }

    public static final int PUBLIC_RADIUS_METERS = 5000;
    public static final int PUBLIC_RESULTS = 6;
    // No photos: each photo is a further paid call, and the photo endpoints need sign-in.
    private static final String PUBLIC_SUMMARY = "places.id,places.primaryType,places.displayName,places.formattedAddress,places.rating,places.userRatingCount,places.businessStatus,places.googleMapsUri,places.attributions";
    @Value("${google.places.public-requests-per-minute:20}") private int publicRequestsPerMinute = 20;
    private long publicWindow;
    private int publicRequests;
    /** Signed-out visitors share a small budget of their own, so they cannot use up the one for signed-in users. */
    private synchronized void reservePublicRequest() {
        long window = System.currentTimeMillis() / 60000;
        if (window != publicWindow) { publicWindow = window; publicRequests = 0; }
        if (publicRequests >= publicRequestsPerMinute) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Nearby search is busy. Please try again in a minute, or sign in to search.");
        publicRequests++;
    }

    /** Nearest spas for a visitor who is not signed in: fixed radius, count and fields. */
    public JsonNode publicNearby(double latitude, double longitude) {
        if (configured()) reservePublicRequest();
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("includedTypes", List.of("spa"));
        body.put("excludedPrimaryTypes", SpaPlaceFilter.EXCLUDED_PRIMARY_TYPES);
        body.put("maxResultCount", PUBLIC_RESULTS);
        body.put("rankPreference", "DISTANCE");
        body.put("locationRestriction", Map.of("circle", Map.of("center", Map.of(
                "latitude", latitude, "longitude", longitude), "radius", PUBLIC_RADIUS_METERS)));
        return call("/places:searchNearby", HttpMethod.POST, body, PUBLIC_SUMMARY);
    }

    /** An owner looking for their own listing: name and address only, no photos or contact details. */
    public JsonNode claimSearch(String query, String country) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("textQuery", query);
        body.put("includedType", "spa");
        body.put("strictTypeFiltering", true);
        body.put("pageSize", 10);
        body.put("regionCode", country.toUpperCase(Locale.ROOT));
        return call("/places:searchText", HttpMethod.POST, body,
                "places.id,places.primaryType,places.displayName,places.formattedAddress,places.businessStatus");
    }

    /** Live details used only to pre-fill the owner's claim form; the owner confirms each field before anything is saved. */
    public JsonNode claimDetails(String placeId) {
        if (!placeId.matches("[A-Za-z0-9_-]{1,255}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Place ID.");
        return call("/places/" + placeId, HttpMethod.GET, null,
                "id,primaryType,displayName,formattedAddress,addressComponents,location,internationalPhoneNumber,websiteUri,businessStatus,editorialSummary");
    }

    public JsonNode locations(String query, String country) {
        return call("/places:searchText", HttpMethod.POST,
                Map.of("textQuery", query, "regionCode", country.toUpperCase(Locale.ROOT), "pageSize", 5),
                "places.id,places.displayName,places.formattedAddress,places.location,places.viewport,places.attributions");
    }
}
