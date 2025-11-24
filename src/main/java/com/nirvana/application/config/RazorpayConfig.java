// src/main/java/com/nirvana/application/config/RazorpayConfig.java
package com.nirvana.application.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Configuration
@EnableConfigurationProperties(RazorpayProperties.class)
@RequiredArgsConstructor
@Slf4j
public class RazorpayConfig {

    private final RazorpayProperties props;

    @Bean
    public RazorpayClient razorpayClient() throws RazorpayException {
        if (!props.isEnabled()) {
            log.warn("Razorpay disabled via config");
            return null;
        }
        log.info("Initializing RazorpayClient for key {}", props.getKeyId());
        return new RazorpayClient(props.getKeyId(), props.getKeySecret());
    }
}
