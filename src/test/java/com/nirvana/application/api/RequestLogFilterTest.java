package com.nirvana.application.api;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLogFilterTest {
    @Test
    void replacesInvalidHeaderAndCleansContextAfterDeniedRequest() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/bookings");
        var response = new MockHttpServletResponse();
        request.addHeader("X-Correlation-ID", "private@example.invalid");
        new RequestLogFilter().doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("correlationId")).matches("[0-9a-f-]{36}");
            assertThat(MDC.get("userId")).isEqualTo("anonymous");
            response.setStatus(401);
        });
        assertThat(response.getHeader("X-Trace-ID")).matches("[0-9a-f]{32}");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void preservesPageCorrelationAndExistingTrace() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/spas/search");
        var response = new MockHttpServletResponse();
        var id = java.util.UUID.randomUUID().toString();
        request.addHeader("X-Correlation-ID", id);
        try (var ignored = MDC.putCloseable("traceId", "0123456789abcdef0123456789abcdef")) {
            new RequestLogFilter().doFilter(request, response, (req, res) -> {});
            assertThat(response.getHeader("X-Trace-ID")).isEqualTo(MDC.get("traceId"));
            assertThat(response.getHeader("X-Correlation-ID")).isEqualTo(id);
        }
    }
}
