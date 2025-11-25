package com.nirvana.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "notifications.sendgrid")
public class SendGridProperties {

    private boolean enabled = false;
    private String apiKey;
    private String fromEmail;
    private String fromName = "Nirvana";
    private String ownerFallbackEmail;
    private String ownerSubject = "New booking confirmed";
    private String customerSubject = "Your booking is confirmed";
}
