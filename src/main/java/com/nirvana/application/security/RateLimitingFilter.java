package com.nirvana.application.security;

import com.nirvana.application.config.RateLimitingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {
    private final RateLimitingProperties properties;
    private final RateLimitService limits;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled() || properties.getWhitelistPaths().contains(request.getRequestURI());
    }

    // Group aliases and resource IDs so switching a URL or spa does not start a new budget.
    static String sensitiveOperation(String method, String path) {
        if (!"POST".equals(method) && !"PUT".equals(method) && !"PATCH".equals(method)) return null;
        String canonical = path.startsWith("/api/") ? path.substring(4) : path;
        if (canonical.startsWith("/auth/") && !canonical.equals("/auth/refresh") && !canonical.equals("/auth/logout"))
            return canonical;
        if (canonical.equals("/me/account/password") || canonical.equals("/me/account/google")
                || canonical.startsWith("/me/mfa/")) return canonical;
        if (canonical.equals("/bookings") || canonical.matches("/spas/[^/]+/appointment-requests"))
            return "booking:create";
        if (canonical.matches("/spas/[^/]+/services/[^/]+/slots/[^/]+/hold")) return "booking:hold";
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String operation = sensitiveOperation(request.getMethod(), path);
        String bucket = operation == null ? path : operation;
        int max = properties.getRequests();
        if (operation != null) max = Math.min(max, properties.getAuthRequests());
        else if (path.equals("/api/public/discovery/nearby") || path.startsWith("/api/public/support/") && "POST".equals(request.getMethod()))
            max = Math.min(max, properties.getPublicDiscoveryRequests());
        // RemoteAddr comes from the container's trusted proxy configuration.
        try {
            boolean allowed = consume("ip:" + request.getRemoteAddr() + "|" + bucket, max, operation);
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (allowed && auth != null && auth.getPrincipal() instanceof UserPrincipal user && properties.getPerUserRequests() != null)
                allowed = consume("user:" + user.getId() + "|" + bucket, properties.getPerUserRequests(), operation);
            if (!allowed) {
                reject(response, 429, "Too many requests. Please wait and try again.");
                return;
            }
        } catch (DataAccessException unavailable) {
            reject(response, 503, "Please try again shortly.");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean consume(String key, int max, String operation) {
        return operation == null ? limits.isAllowed(key, max) : limits.isAllowedSensitive(key, max);
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", Integer.toString(properties.getWindowSeconds()));
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
