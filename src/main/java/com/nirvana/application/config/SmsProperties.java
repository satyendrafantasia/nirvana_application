package com.nirvana.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "sms")
public class SmsProperties {

    private boolean enabled = true;
    private String provider;

    /** Phone codes are sent and checked by Firebase in the browser; the server only checks Google's proof. */
    public boolean isFirebase() {
        return "FIREBASE".equalsIgnoreCase(provider);
    }

    /** Whether a code can reach a phone at all: by our own SMS, or by Firebase. */
    public boolean phoneCodesAvailable() {
        return enabled || isFirebase();
    }
}
