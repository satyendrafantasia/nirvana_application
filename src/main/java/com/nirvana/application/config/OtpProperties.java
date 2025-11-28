package com.nirvana.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "otp")
public class OtpProperties {

    private int length = 6;
    private int expiryMinutes = 10;
    private int maxAttempts = 5;
}
