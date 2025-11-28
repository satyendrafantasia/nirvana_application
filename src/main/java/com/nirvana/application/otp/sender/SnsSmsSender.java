package com.nirvana.application.otp.sender;

import com.nirvana.application.config.SmsProperties;
import com.nirvana.application.otp.exception.SmsSendException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SnsException;

@Slf4j
@Component
@RequiredArgsConstructor
public class SnsSmsSender implements SmsSender {

    private final SnsClient snsClient;
    private final SmsProperties smsProperties;

    @Override
    public void sendOtpSms(String phoneNumber, String message) {
        if (!smsProperties.isEnabled()) {
            log.info("SMS sending disabled; skipping SNS publish for {}", phoneNumber);
            return;
        }
        try {
            PublishRequest request = PublishRequest.builder()
                    .phoneNumber(phoneNumber)
                    .message(message)
                    .build();
            snsClient.publish(request);
            log.info("OTP SMS published to {}", phoneNumber);
        } catch (SnsException ex) {
            log.error("Failed to send OTP SMS via SNS: {}", ex.awsErrorDetails().errorMessage(), ex);
            throw new SmsSendException("Failed to send SMS via AWS SNS", ex);
        } catch (Exception ex) {
            log.error("Unexpected error sending OTP SMS", ex);
            throw new SmsSendException("Failed to send SMS via AWS SNS", ex);
        }
    }
}
