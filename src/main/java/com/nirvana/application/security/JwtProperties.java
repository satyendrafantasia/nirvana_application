package com.nirvana.application.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@org.springframework.validation.annotation.Validated
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {
    /**
     * Symmetric secret used for signing/verifying JWTs. Should be at least 256 bits for HS256.
     */
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(min = 32, message = "Configure a signing secret of at least 32 characters")
    private String secret;

    /**
     * Expiration in minutes.
     */
    @jakarta.validation.constraints.Positive
    private long expirationMinutes = 15;

    /**
     * Refresh token validity in days.
     */
    @jakarta.validation.constraints.Positive
    private long refreshExpirationDays = 30;

    @jakarta.validation.constraints.NotBlank
    private String issuer = "nirvana-wellness";

    @jakarta.validation.constraints.NotBlank
    private String audience = "nirvana-api";

    /**
     * Header used to convey device fingerprint for binding tokens.
     */
    private String deviceFingerprintHeader = "X-Device-Fingerprint";
}
