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

    /**
     * Refresh token validity in days.
     */
    private long refreshExpirationDays = 30;

    /**
     * Header used to convey device fingerprint for binding tokens.
     */
    private String deviceFingerprintHeader = "X-Device-Fingerprint";
}
