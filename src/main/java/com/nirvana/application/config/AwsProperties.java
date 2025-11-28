package com.nirvana.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "aws")
public class AwsProperties {

    private String region;
    private Credentials credentials = new Credentials();

    @Getter
    @Setter
    public static class Credentials {
        /**
         * Prefer using environment-based credentials or IAM roles in production.
         */
        private String accessKeyId;
        private String secretAccessKey;
    }
}
