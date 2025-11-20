// java
// `src/main/java/com/nirvana/application/security/JwtTokenProvider.java`
package com.nirvana.application.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    private final JwtProperties props;
    private final SecretKey key;

    public JwtTokenProvider(JwtProperties props) {
        this.props = props;
        // Accept raw secret or base64; if base64 decoding fails, use raw bytes
        byte[] secretBytes = props.getSecret() != null ? props.getSecret().getBytes(StandardCharsets.UTF_8) : new byte[0];
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateToken(Authentication authentication) {
        String username = authentication.getName();
        Collection<String> roles = authentication.getAuthorities()
                .stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList());
        long now = System.currentTimeMillis();
        Date issuedAt = new Date(now);
        Date expiry = new Date(now + props.getExpirationMs());

        return Jwts.builder()
                .setSubject(username)
                .setIssuer(props.getIssuer())
                .claim("roles", roles)
                .setIssuedAt(issuedAt)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public String getUsername(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody();
        return claims.getSubject();
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody();
        Object roles = claims.get("roles");
        if (roles instanceof List<?>) {
            return ((List<?>) roles).stream().map(Object::toString).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public Date getExpiration(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody();
        return claims.getExpiration();
    }

    public String resolveToken(String headerValue) {
        if (headerValue == null) return null;
        String prefix = props.getTokenPrefix();
        if (headerValue.startsWith(prefix)) {
            return headerValue.substring(prefix.length());
        }
        return headerValue;
    }
}
