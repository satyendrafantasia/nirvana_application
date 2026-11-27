package com.nirvana.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "email")
@org.springframework.validation.annotation.Validated
public class EmailProperties {

    @jakarta.validation.constraints.AssertTrue(message = "EMAIL_FROM_ADDRESS must be set when email delivery is enabled")
    public boolean isSenderConfigured() {
        return !enabled || (fromAddress != null && !fromAddress.isBlank());
    }
    private boolean enabled;
    private String provider;
    private String fromAddress;
    private String fromName;
}
