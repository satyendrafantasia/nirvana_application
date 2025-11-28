package com.nirvana.application.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.sns.SnsClient;

@Configuration
@EnableConfigurationProperties(AwsProperties.class)
@RequiredArgsConstructor
public class AwsConfig {

    private final AwsProperties awsProperties;

    @Bean
    public Region awsRegion() {
        return Region.of(awsProperties.getRegion());
    }

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                awsProperties.getCredentials().getAccessKeyId(),
                awsProperties.getCredentials().getSecretAccessKey());
        // In production, prefer DefaultCredentialsProvider or IAM roles instead of static keys.
        return StaticCredentialsProvider.create(credentials);
    }

    @Bean
    public SnsClient snsClient(Region awsRegion, AwsCredentialsProvider awsCredentialsProvider) {
        return SnsClient.builder()
                .region(awsRegion)
                .credentialsProvider(awsCredentialsProvider)
                .build();
    }

    @Bean
    public SesClient sesClient(Region awsRegion, AwsCredentialsProvider awsCredentialsProvider) {
        return SesClient.builder()
                .region(awsRegion)
                .credentialsProvider(awsCredentialsProvider)
                .build();
    }
}
