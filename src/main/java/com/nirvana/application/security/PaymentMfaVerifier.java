package com.nirvana.application.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class PaymentMfaVerifier {
    static final String VERIFIED_SESSION = PaymentMfaVerifier.class.getName() + ".verifiedSession";
    private final MfaProperties properties;

    public PaymentMfaVerifier(MfaProperties properties) {
        this.properties = properties;
    }

    public void verifyPaymentChallenge() {
        if (!properties.isEnabled()) return;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && Boolean.TRUE.equals(attributes.getRequest().getAttribute(VERIFIED_SESSION))) return;
        throw new AccessDeniedException("Sign in with your password and registered second factor to continue payment.");
    }

    @ConfigurationProperties(prefix = "security.mfa")
    public static class MfaProperties {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
