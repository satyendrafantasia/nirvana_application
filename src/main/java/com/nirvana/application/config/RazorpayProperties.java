// src/main/java/com/nirvana/application/config/RazorpayProperties.java
package com.nirvana.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "payments.razorpay")
public class RazorpayProperties {

    public boolean readyForCheckout() {
        return credentialsConfigured() && org.springframework.util.StringUtils.hasText(webhookSecret);
    }

    public void requireCheckout() {
        if (!readyForCheckout()) throw new IllegalStateException("Online payment is unavailable. Please choose another payment method.");
    }

    public boolean credentialsConfigured() {
        return enabled && org.springframework.util.StringUtils.hasText(keyId)
                && org.springframework.util.StringUtils.hasText(keySecret);
    }

    private boolean enabled;
    private String keyId;
    private String keySecret;
    private String webhookSecret;
    private String currency = "INR";
    private boolean autoCapture = true;
    private String descriptionPrefix = "Nirvana Booking";
}
