package com.nirvana.application.security;

import com.nirvana.application.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;

@Component
public class JwtTokenService {

    private final JwtProperties properties;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.getExpirationMinutes(), ChronoUnit.MINUTES);

        return Jwts.builder()
                .setSubject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("roles", user.getRoles())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiry))
                .signWith(signingKey())
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    @SuppressWarnings("unchecked")
    public Set<String> extractRoles(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof Set<?> set) {
            return set.stream().map(Object::toString).collect(java.util.stream.Collectors.toSet());
        }
        if (roles instanceof java.util.Collection<?> collection) {
            return collection.stream().map(Object::toString).collect(java.util.stream.Collectors.toSet());
        }
        return java.util.Collections.emptySet();
    }

    private Key signingKey() {
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
