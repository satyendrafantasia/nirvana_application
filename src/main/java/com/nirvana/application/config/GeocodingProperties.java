package com.nirvana.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "geocoding")
public class GeocodingProperties {

    /** Base URL for the geocoding provider (e.g., https://api.example.com). */
    private String baseUrl;

    /** API key or bearer token to authenticate with the geocoding provider. */
    private String apiKey;

    /** Whether remote geocoding is enabled. */
    private boolean enabled = false;

    /** Connection timeout in milliseconds. */
    private int connectTimeoutMs = 4000;

    /** Read timeout in milliseconds. */
    private int readTimeoutMs = 5000;
}
