package com.nirvana.application.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;

@Component
public class DeviceFingerprintResolver {

    private final JwtProperties jwtProperties;

    public DeviceFingerprintResolver(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String resolveFingerprint() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String explicit = request.getHeader(jwtProperties.getDeviceFingerprintHeader());
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim();
        }
        String userAgent = Optional.ofNullable(request.getHeader("User-Agent")).orElse("unknown");
        String ip = Optional.ofNullable(request.getHeader("X-Forwarded-For"))
                .map(h -> h.split(",")[0])
                .orElse(request.getRemoteAddr());
        String seed = userAgent + "|" + ip;
        return hash(seed);
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private String hash(String seed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(seed.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to hash fingerprint", e);
        }
    }
}
