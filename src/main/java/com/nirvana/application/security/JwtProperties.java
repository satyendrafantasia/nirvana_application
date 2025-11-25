package com.nirvana.application.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {
    /**
     * Symmetric secret used for signing/verifying JWTs. Should be at least 256 bits for HS256.
     */
    private String secret;

    /**
     * Expiration in minutes.
     */
    private long expirationMinutes = 60 * 24; // 24 hours by default
}
