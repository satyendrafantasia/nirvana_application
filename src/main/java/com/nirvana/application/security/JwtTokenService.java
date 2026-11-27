package com.nirvana.application.security;

import com.nirvana.application.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
public class JwtTokenService {
    private final JwtProperties properties;
    private final SecretKeySpec key;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
        byte[] bytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalArgumentException("JWT signing key requires at least 32 bytes");
        this.key = new SecretKeySpec(bytes, "HmacSHA256");
    }

    public String generateToken(User user, String deviceFingerprint, boolean mfaVerified, String sessionFamily) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(String.valueOf(user.getId()))
                .setIssuer(properties.getIssuer())
                .setAudience(properties.getAudience())
                .claim("mfa", mfaVerified)
                .claim("sid", sessionFamily)
                .claim("dfp", deviceFingerprint)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(properties.getExpirationMinutes(), ChronoUnit.MINUTES)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseClaims(String token) {
        var signed = Jwts.parserBuilder().setSigningKey(key)
                .requireIssuer(properties.getIssuer()).requireAudience(properties.getAudience())
                .build().parseClaimsJws(token);
        var claims = signed.getBody();
        if (!"HS256".equals(signed.getHeader().getAlgorithm()) || claims.getExpiration() == null
                || claims.getIssuedAt() == null || claims.getSubject() == null || claims.get("sid",String.class) == null) {
            throw new JwtException("Invalid session claims");
        }
        return claims;
    }
}
