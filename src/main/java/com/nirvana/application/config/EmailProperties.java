package com.nirvana.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "email")
public class EmailProperties {

    private boolean enabled = true;
    private String provider;
    private String fromAddress;
    private String fromName;
}
