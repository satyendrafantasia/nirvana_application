package com.nirvana.application.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({OtpProperties.class, EmailProperties.class, SmsProperties.class})
public class OtpModuleConfig {
}
