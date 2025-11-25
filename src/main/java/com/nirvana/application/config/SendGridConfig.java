package com.nirvana.application.config;

import com.sendgrid.SendGrid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SendGridProperties.class)
@RequiredArgsConstructor
@Slf4j
public class SendGridConfig {

    private final SendGridProperties properties;

    @Bean
    @ConditionalOnProperty(prefix = "notifications.sendgrid", name = "enabled", havingValue = "true")
    public SendGrid sendGrid() {
        log.info("Initializing SendGrid client for fromEmail={} (enabled={})", properties.getFromEmail(), properties.isEnabled());
        return new SendGrid(properties.getApiKey());
    }
}
