package com.nirvana.application.config;

import com.nirvana.application.security.BrowserSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

@Configuration
public class BrowserOrigins {
    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins},${seo.public-origin:}") String configured) {
        List<String> origins = Arrays.stream(configured.split(",")).map(String::trim)
                .filter(s -> !s.isBlank()).map(BrowserOrigins::exactOrigin).distinct().toList();
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Accept","Content-Type","Authorization","Idempotency-Key",
                "X-Device-Fingerprint","X-Correlation-ID","X-Support-Key",
                BrowserSession.HEADER));
        cors.setExposedHeaders(List.of("X-Correlation-ID","X-Trace-ID","Retry-After","Content-Disposition"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    private static String exactOrigin(String input) {
        URI uri = URI.create(input);
        boolean loopback = List.of("localhost","127.0.0.1","[::1]").contains(String.valueOf(uri.getHost()));
        if (uri.getHost() == null || input.contains("*") || uri.getUserInfo() != null
                || !uri.getRawPath().isEmpty() || uri.getQuery() != null || uri.getFragment() != null
                || !("https".equals(uri.getScheme()) || (loopback && "http".equals(uri.getScheme()))))
            throw new IllegalArgumentException("CORS origins must be explicit HTTPS origins or HTTP loopback origins");
        return input;
    }
}
