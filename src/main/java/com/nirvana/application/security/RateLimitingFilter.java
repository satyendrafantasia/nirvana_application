package com.nirvana.application.security;

import com.nirvana.application.config.RateLimitingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import com.nirvana.application.security.UserPrincipal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final String CAPTCHA_HEADER = "X-Captcha-Token";

    private final RateLimitingProperties properties;
    private final RateLimitService rateLimitService;
    private final Clock clock = Clock.systemUTC();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return properties.getWhitelistPaths().stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = resolveKey(request);
        Instant now = clock.instant();
        String userKey = resolveUserKey(request);
        String path = request.getRequestURI();
        boolean allowed = (path.equals("/api/public/discovery/nearby") || path.startsWith("/api/public/support/") && "POST".equals(request.getMethod()))
                // Open to everyone and each call is paid for at Google, so it gets the tightest limit.
                ? rateLimitService.isAllowed(key, Math.min(properties.getPublicDiscoveryRequests(), properties.getRequests()))
                : isAuthPath(path)
                ? rateLimitService.isAllowed(key, Math.min(properties.getAuthRequests(), properties.getRequests()))
                : rateLimitService.isAllowed(key);
        boolean userAllowed = true;
        if (userKey != null && properties.getPerUserRequests() != null) {
            userAllowed = rateLimitService.isAllowed(userKey, properties.getPerUserRequests());
        }

        if (!allowed || !userAllowed) {
            String captchaToken = request.getHeader(CAPTCHA_HEADER);
            String configured = properties.getCaptchaBypassToken();
            boolean captchaProvided = configured != null && !configured.isBlank() && captchaToken != null
                    && MessageDigest.isEqual(captchaToken.getBytes(StandardCharsets.UTF_8),
                            configured.getBytes(StandardCharsets.UTF_8));
            if (!captchaProvided) {
                Instant blockedUntil = now.plusSeconds(properties.getBlockSeconds());
                log.warn("Request rate limit reached");
                reject(response, "Too many requests. Please wait and try again.", blockedUntil);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveKey(HttpServletRequest request) {
        // Never read X-Forwarded-For here: any client can set it. Behind a trusted proxy,
        // set server.forward-headers-strategy=native so the container resolves the address.
        return request.getRemoteAddr() + "|" + request.getRequestURI();
    }

    private boolean isAuthPath(String path) {
        if (path.equals("/api/me/account/password")) {
            return true; // checks the current password, so it is a guessing target too
        }
        if (!path.startsWith("/api/auth/") && !path.startsWith("/auth/")) {
            return false;
        }
        // Credential-guessing targets only; session reads and token refresh keep the general limit.
        return path.contains("/login") || path.contains("/register") || path.contains("/password") || path.contains("/otp");
    }

    private String resolveUserKey(HttpServletRequest request) {
        if (properties.getPerUserRequests() == null) {
            return null;
        }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return "user:" + principal.getId() + "|" + request.getRequestURI();
        }
        return null;
    }

    private void reject(HttpServletResponse response, String message, Instant blockedUntil) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(properties.getBlockSeconds()));
        response.getWriter().write("{\"error\":\"" + message + "\",\"blockedUntil\":\"" + blockedUntil + "\"}");
    }

}
