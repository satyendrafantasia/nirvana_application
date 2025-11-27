package com.nirvana.application.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class PaymentMfaVerifier {

    private final MfaProperties properties;

    public PaymentMfaVerifier(MfaProperties properties) {
        this.properties = properties;
    }

    public void verifyPaymentChallenge() {
        if (!properties.isEnabled()) {
            return;
        }
        HttpServletRequest request = currentRequest();
        if (request == null) {
            throw new IllegalStateException("Missing request context for MFA validation");
        }
        String provided = request.getHeader(properties.getHeader());
        if (provided == null || !provided.equals(properties.getBypassToken())) {
            throw new IllegalArgumentException("Payment MFA challenge missing or invalid");
        }
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    @Component
    @ConfigurationProperties(prefix = "security.mfa")
    public static class MfaProperties {
        private boolean enabled = false;
        private String header = "X-MFA-Token";
        private String bypassToken = "demo-mfa";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHeader() {
            return header;
        }

        public void setHeader(String header) {
            this.header = header;
        }

        public String getBypassToken() {
            return bypassToken;
        }

        public void setBypassToken(String bypassToken) {
            this.bypassToken = bypassToken;
        }
    }
}
