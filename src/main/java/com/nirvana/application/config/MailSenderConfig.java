package com.nirvana.application.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.nio.charset.Charset;
import java.util.Properties;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
@ConditionalOnProperty(prefix = "notifications.sendgrid", name = "enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class MailSenderConfig {

    private final MailProperties mailProperties;

    @Bean
    @ConditionalOnMissingBean(JavaMailSender.class)
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        if (mailProperties.getHost() == null || mailProperties.getHost().isBlank()) {
            log.warn("spring.mail.host is not configured; defaulting JavaMailSender host to localhost");
            mailSender.setHost("localhost");
        } else {
            mailSender.setHost(mailProperties.getHost());
            if (mailProperties.getPort() != null) {
                mailSender.setPort(mailProperties.getPort());
            }
        }

        mailSender.setUsername(mailProperties.getUsername());
        mailSender.setPassword(mailProperties.getPassword());

        Charset defaultEncoding = mailProperties.getDefaultEncoding();
        if (defaultEncoding != null) {
            mailSender.setDefaultEncoding(defaultEncoding.name());
        }

        Properties javaMailProps = new Properties();
        javaMailProps.putAll(mailProperties.getProperties());
        mailSender.setJavaMailProperties(javaMailProps);

        return mailSender;
    }
}
