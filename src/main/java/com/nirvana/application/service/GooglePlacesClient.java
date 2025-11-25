package com.nirvana.application.service;

import com.nirvana.application.model.dto.GooglePlaceDetailsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
@RequiredArgsConstructor
public class GooglePlacesClient {

    private final RestTemplateBuilder restTemplateBuilder;

    @Value("${google.places.api-key:}")
    private String apiKey;

    @Value("${google.places.base-url:https://maps.googleapis.com/maps/api/place}")
    private String baseUrl;

    public GooglePlaceDetailsResponse fetchPlaceDetails(String placeId) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Google Places API key is not configured");
        }
        RestTemplate restTemplate = restTemplateBuilder.build();
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/details/json")
                .queryParam("place_id", placeId)
                .queryParam("fields", "place_id,name,formatted_address,international_phone_number,website,address_component,geometry")
                .queryParam("key", apiKey)
                .build()
                .toUri();

        try {
            return restTemplate.getForObject(uri, GooglePlaceDetailsResponse.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Failed to call Google Places API", e);
        }
    }
}
