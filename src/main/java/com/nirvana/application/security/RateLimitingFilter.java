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

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final String CAPTCHA_HEADER = "X-Captcha-Token";

    private final RateLimitingProperties properties;
    private final Clock clock = Clock.systemUTC();
    private final Map<String, RequestWindow> windows = new ConcurrentHashMap<>();

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

        RequestWindow window = windows.computeIfAbsent(key, k -> new RequestWindow());
        synchronized (window) {
            if (window.blockedUntil != null && now.isBefore(window.blockedUntil)) {
                reject(response, "Rate limit exceeded. Retry after cool-off.", window.blockedUntil);
                return;
            }

            String captchaToken = request.getHeader(CAPTCHA_HEADER);
            boolean captchaProvided = captchaToken != null && captchaToken.equals(properties.getCaptchaBypassToken());

            window.evictOld(now, properties.getWindowSeconds());
            if (window.requests.size() >= properties.getRequests() && !captchaProvided) {
                window.blockedUntil = now.plusSeconds(properties.getBlockSeconds());
                log.warn("Rate limit triggered for key {} on path {}", key, request.getRequestURI());
                reject(response, "Too many requests. Provide CAPTCHA token or wait.", window.blockedUntil);
                return;
            }

            window.requests.addLast(now);
        }

        filterChain.doFilter(request, response);
    }

    private String resolveKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return ip + "|" + request.getRequestURI();
    }

    private void reject(HttpServletResponse response, String message, Instant blockedUntil) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(properties.getBlockSeconds()));
        response.setHeader("X-Captcha-Required", "true");
        response.getWriter().write("{\"error\":\"" + message + "\",\"blockedUntil\":\"" + blockedUntil + "\"}");
    }

    private static class RequestWindow {
        private final Deque<Instant> requests = new ArrayDeque<>();
        private Instant blockedUntil;

        void evictOld(Instant now, int windowSeconds) {
            Instant threshold = now.minusSeconds(windowSeconds);
            while (!requests.isEmpty() && requests.peekFirst().isBefore(threshold)) {
                requests.removeFirst();
            }
        }
    }
}
