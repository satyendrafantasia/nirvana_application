package com.nirvana.application.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RequestLogFilter extends OncePerRequestFilter {
    private static final Pattern UUID_FORMAT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    public static final String USER_ATTRIBUTE = RequestLogFilter.class.getName() + ".userId";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        long start = System.nanoTime();
        String correlation = request.getHeader("X-Correlation-ID");
        if (correlation == null || !UUID_FORMAT.matcher(correlation).matches()) {
            correlation = UUID.randomUUID().toString();
        }
        String trace = MDC.get("traceId");
        if (trace == null) trace = UUID.randomUUID().toString().replace("-", "");
        try {
            MDC.put("correlationId", correlation);
            MDC.put("traceId", trace);
            MDC.put("userId", "anonymous");
            response.setHeader("X-Correlation-ID", correlation);
            response.setHeader("X-Trace-ID", trace);
            String method = request.getMethod();
            if (!method.matches("[A-Z]{1,12}")) method = "OTHER";
            log.info("event=request.started method={}", method);
            boolean completed = false;
            try {
                chain.doFilter(request, response);
                completed = true;
            } finally {
                Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
                String operation = handler instanceof HandlerMethod h
                        ? h.getBeanType().getSimpleName() + "." + h.getMethod().getName() : "unmapped";
                Object actor = request.getAttribute(USER_ATTRIBUTE);
                if (actor instanceof Long id) MDC.put("userId", id.toString());
                int status = completed ? response.getStatus() : Math.max(500, response.getStatus());
                log.info("event=request.finished operation={} status={} durationMs={}", operation, status,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            }
        } finally {
            if (previous == null) MDC.clear();
            else MDC.setContextMap(previous);
        }
    }
}
