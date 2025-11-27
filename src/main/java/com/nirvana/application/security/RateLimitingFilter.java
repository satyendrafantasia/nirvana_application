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
        boolean allowed = rateLimitService.isAllowed(key);
        boolean userAllowed = true;
        if (userKey != null && properties.getPerUserRequests() != null) {
            userAllowed = rateLimitService.isAllowed(userKey, properties.getPerUserRequests());
        }

        if (!allowed || !userAllowed) {
            String captchaToken = request.getHeader(CAPTCHA_HEADER);
            boolean captchaProvided = captchaToken != null && captchaToken.equals(properties.getCaptchaBypassToken());
            if (!captchaProvided) {
                Instant blockedUntil = now.plusSeconds(properties.getBlockSeconds());
                log.warn("Rate limit triggered for key {} on path {}", userKey != null ? userKey : key, request.getRequestURI());
                reject(response, "Too many requests. Provide CAPTCHA token or wait.", blockedUntil);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return ip + "|" + request.getRequestURI();
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
        response.setHeader("X-Captcha-Required", "true");
        response.getWriter().write("{\"error\":\"" + message + "\",\"blockedUntil\":\"" + blockedUntil + "\"}");
    }

}
