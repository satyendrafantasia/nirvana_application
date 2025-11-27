package com.nirvana.application.service.impl;

import com.nirvana.application.config.GeocodingProperties;
import com.nirvana.application.model.Address;
import com.nirvana.application.service.AddressNormalizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoOpAddressNormalizationService implements AddressNormalizationService {

    private static final String DEFAULT_GEOCODING_PATH = "/geocode";

    private final RestTemplateBuilder restTemplateBuilder;
    private final GeocodingProperties geocodingProperties;

    @Override
    public Address normalize(Address input) {
        if (input == null) return null;

        trimWhitespace(input);
        enrichFromGeocoder(input);
        populateFormattedAddress(input);
        populateCountryCode(input);
        populateTimezone(input);

        log.debug("Address normalized: {}", input);
        return input;
    }

    private void enrichFromGeocoder(Address input) {
        if (!geocodingProperties.isEnabled()) {
            log.debug("Geocoding disabled; returning heuristically normalized address");
            return;
        }

        String baseUrl = geocodingProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("Geocoding base URL is not configured; skipping geocoding");
            return;
        }

        String query = buildQuery(input);
        if (query.isBlank()) {
            log.warn("Unable to build geocoding query for address: {}", input);
            return;
        }

        try {
            RestTemplate restTemplate = restTemplateBuilder
                    .setConnectTimeout(Duration.ofMillis(geocodingProperties.getConnectTimeoutMs()))
                    .setReadTimeout(Duration.ofMillis(geocodingProperties.getReadTimeoutMs()))
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(MediaType.parseMediaTypes(MediaType.APPLICATION_JSON_VALUE));
            if (geocodingProperties.getApiKey() != null && !geocodingProperties.getApiKey().isBlank()) {
                headers.setBearerAuth(geocodingProperties.getApiKey());
            }

            String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .path(DEFAULT_GEOCODING_PATH)
                    .queryParam("q", query)
                    .build()
                    .toUriString();

            RequestEntity<Void> request = new RequestEntity<>(headers, HttpMethod.GET, UriComponentsBuilder.fromUriString(url).build(true).toUri());
            ResponseEntity<GeocodingResponse> response = restTemplate.exchange(request, GeocodingResponse.class);
            GeocodingResponse body = response.getBody();
            if (body == null) {
                log.warn("Geocoding provider returned empty response for query: {}", query);
                return;
            }

            mergeIfPresent(input, body);
        } catch (RestClientException ex) {
            log.error("Geocoding lookup failed for query {}", query, ex);
        }
    }

    private void mergeIfPresent(Address target, GeocodingResponse geocoded) {
        target.setFormattedAddress(firstNonBlank(target.getFormattedAddress(), geocoded.formattedAddress()));
        target.setLatitude(optionalBigDecimal(geocoded.latitude()).orElse(target.getLatitude()));
        target.setLongitude(optionalBigDecimal(geocoded.longitude()).orElse(target.getLongitude()));
        target.setCountry(firstNonBlank(target.getCountry(), geocoded.country()));
        target.setCountryCode(firstNonBlank(target.getCountryCode(), geocoded.countryCode()));
        target.setCity(firstNonBlank(target.getCity(), geocoded.city()));
        target.setState(firstNonBlank(target.getState(), geocoded.state()));
        target.setPostalCode(firstNonBlank(target.getPostalCode(), geocoded.postalCode()));
        target.setLocality(firstNonBlank(target.getLocality(), geocoded.locality()));
        target.setTimezone(firstNonBlank(target.getTimezone(), geocoded.timezone()));
        target.setGooglePlaceId(firstNonBlank(target.getGooglePlaceId(), geocoded.placeId()));
        target.setGeoSource(firstNonBlank(target.getGeoSource(), geocoded.source()));
    }

    private Optional<BigDecimal> optionalBigDecimal(BigDecimal value) {
        return Optional.ofNullable(value);
    }

    private String firstNonBlank(String existing, String candidate) {
        if (notBlank(existing)) {
            return existing;
        }
        return trimToNull(candidate);
    }

    private String buildQuery(Address input) {
        StringBuilder sb = new StringBuilder();
        if (notBlank(input.getAddressLine())) sb.append(input.getAddressLine());
        if (notBlank(input.getAddressLine2())) sb.append(", ").append(input.getAddressLine2());
        if (notBlank(input.getLocality())) sb.append(", ").append(input.getLocality());
        if (notBlank(input.getCity())) sb.append(", ").append(input.getCity());
        if (notBlank(input.getState())) sb.append(", ").append(input.getState());
        if (notBlank(input.getPostalCode())) sb.append(" ").append(input.getPostalCode());
        if (notBlank(input.getCountry())) sb.append(", ").append(input.getCountry());
        return sb.toString().trim();
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private void trimWhitespace(Address input) {
        input.setAddressLine(trimToNull(input.getAddressLine()));
        input.setAddressLine2(trimToNull(input.getAddressLine2()));
        input.setLocality(trimToNull(input.getLocality()));
        input.setCity(trimToNull(input.getCity()));
        input.setState(trimToNull(input.getState()));
        input.setPostalCode(trimToNull(input.getPostalCode()));
        input.setCountry(trimToNull(input.getCountry()));
        input.setCountryCode(trimToNull(input.getCountryCode()));
    }

    private void populateFormattedAddress(Address input) {
        if (notBlank(input.getFormattedAddress())) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (notBlank(input.getAddressLine())) sb.append(input.getAddressLine());
        if (notBlank(input.getAddressLine2())) sb.append(", ").append(input.getAddressLine2());
        if (notBlank(input.getLocality())) sb.append(", ").append(input.getLocality());
        if (notBlank(input.getCity())) sb.append(", ").append(input.getCity());
        if (notBlank(input.getState())) sb.append(", ").append(input.getState());
        if (notBlank(input.getPostalCode())) sb.append(" - ").append(input.getPostalCode());
        if (notBlank(input.getCountry())) sb.append(", ").append(input.getCountry());
        input.setFormattedAddress(sb.toString());
    }

    private void populateCountryCode(Address input) {
        if (notBlank(input.getCountryCode())) {
            input.setCountryCode(input.getCountryCode().toUpperCase());
            return;
        }

        if (input.getCountry() == null) {
            return;
        }

        switch (input.getCountry().trim().toLowerCase()) {
            case "india":
            case "bharat":
                input.setCountryCode("IN");
                break;
            case "united states":
            case "usa":
            case "united states of america":
                input.setCountryCode("US");
                break;
            case "united kingdom":
            case "uk":
                input.setCountryCode("GB");
                break;
            default:
                break;
        }
    }

    private void populateTimezone(Address input) {
        if (notBlank(input.getTimezone())) {
            return;
        }

        if ("IN".equalsIgnoreCase(input.getCountryCode())) {
            input.setTimezone("Asia/Kolkata");
        } else if ("US".equalsIgnoreCase(input.getCountryCode())) {
            input.setTimezone("America/New_York");
        } else if ("GB".equalsIgnoreCase(input.getCountryCode())) {
            input.setTimezone("Europe/London");
        } else {
            input.setTimezone("UTC");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

record GeocodingResponse(String formattedAddress,
                         String country,
                         String countryCode,
                         String city,
                         String state,
                         String postalCode,
                         String locality,
                         String timezone,
                         String placeId,
                         BigDecimal latitude,
                         BigDecimal longitude,
                         String source) {
}

