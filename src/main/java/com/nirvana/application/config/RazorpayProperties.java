// src/main/java/com/nirvana/application/config/RazorpayProperties.java
package com.nirvana.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "payments.razorpay")
public class RazorpayProperties {

    private boolean enabled;
    private String keyId;
    private String keySecret;
    private String webhookSecret;
    private String currency = "INR";
    private boolean autoCapture = true;
    private String descriptionPrefix = "Nirvana Booking";
}
