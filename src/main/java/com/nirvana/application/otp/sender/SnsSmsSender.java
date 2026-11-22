package com.nirvana.application.otp.sender;

import com.nirvana.application.config.SmsProperties;
import com.nirvana.application.otp.exception.SmsSendException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.util.HashMap;
import java.util.Map;

/**
 * Sends OTP codes through AWS SNS as transactional SMS. Indian carriers deliver business SMS only
 * from a DLT-registered sender and template, so the business's sender ID and entity ID (shared with
 * the spa booking alerts) and the OTP template ID are passed with each message when set.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sms.provider", havingValue = "AWS_SNS", matchIfMissing = true)
@RequiredArgsConstructor
public class SnsSmsSender implements SmsSender {

    private final SnsClient snsClient;
    private final SmsProperties smsProperties;

    @Value("${spa-alerts.sms.sender-id:}")
    private String senderId;
    @Value("${spa-alerts.sms.dlt-entity-id:}")
    private String entityId;
    @Value("${sms.otp-template-id:}")
    private String otpTemplateId;

    @Override
    public void sendOtpSms(String phoneNumber, String message) {
        if (!smsProperties.isEnabled()) {
            log.debug("Notification delivery processed");
            return;
        }
        try {
            Map<String, MessageAttributeValue> attributes = new HashMap<>();
            attributes.put("AWS.SNS.SMS.SMSType", text("Transactional"));
            if (StringUtils.hasText(senderId)) attributes.put("AWS.SNS.SMS.SenderID", text(senderId));
            if (StringUtils.hasText(entityId)) attributes.put("AWS.MM.SMS.EntityId", text(entityId));
            if (StringUtils.hasText(otpTemplateId)) attributes.put("AWS.MM.SMS.TemplateId", text(otpTemplateId));
            PublishRequest request = PublishRequest.builder()
                    .phoneNumber(phoneNumber)
                    .message(message)
                    .messageAttributes(attributes)
                    .build();
            snsClient.publish(request);
            log.debug("Notification delivery processed");
        } catch (SnsException ex) {
            log.error("Notification delivery failed");
            throw new SmsSendException("Failed to send SMS via AWS SNS", ex);
        } catch (Exception ex) {
            log.error("Unexpected error sending OTP SMS", ex);
            throw new SmsSendException("Failed to send SMS via AWS SNS", ex);
        }
    }

    private static MessageAttributeValue text(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }
}
