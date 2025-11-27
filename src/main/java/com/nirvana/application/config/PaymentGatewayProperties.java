package com.nirvana.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "payments.gateway")
public class PaymentGatewayProperties {

    /** Base URL for the upstream payment gateway REST API. */
    private String baseUrl;

    /** API key or token used to authenticate with the gateway. */
    private String apiKey;

    /** Whether payment gateway calls are enabled. */
    private boolean enabled = false;

    /** Connection timeout in milliseconds. */
    private int connectTimeoutMs = 5000;

    /** Read timeout in milliseconds. */
    private int readTimeoutMs = 5000;
}

