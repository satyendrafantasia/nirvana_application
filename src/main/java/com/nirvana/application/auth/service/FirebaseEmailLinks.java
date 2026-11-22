package com.nirvana.application.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Component
public class FirebaseEmailLinks {
    private final RestTemplate http;
    private final String apiKey;

    public FirebaseEmailLinks(RestTemplateBuilder builder, @Value("${firebase.web-api-key:}") String apiKey) {
        this.http = builder.connectTimeout(Duration.ofSeconds(5)).readTimeout(Duration.ofSeconds(15)).build();
        this.apiKey = apiKey;
    }

    public String redeem(String email, String code) {
        if (apiKey.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email links are not configured.");
        try {
            JsonNode result = http.postForObject("https://identitytoolkit.googleapis.com/v1/accounts:signInWithEmailLink?key={key}",
                    Map.of("email", email, "oobCode", code), JsonNode.class, apiKey);
            if (result == null || result.path("idToken").asText("").isBlank()
                    || result.hasNonNull("mfaPendingCredential") || result.path("localId").asText("").isBlank()
                    || !email.equalsIgnoreCase(result.path("email").asText("")))
                throw invalid();
            return result.path("email").asText().toLowerCase(Locale.ROOT);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Wait before requesting another link.");
        } catch (HttpClientErrorException e) {
            throw invalid();
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Firebase could not be reached. Please try again.");
        }
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Check the email address or request a fresh link. The link may have expired or already been used.");
    }
}
