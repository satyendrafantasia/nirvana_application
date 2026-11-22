package com.nirvana.application.service;

import com.nirvana.application.config.SmsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Sends booking SMS to spas through AWS SNS as transactional messages. Indian carriers deliver
 * business SMS only from a DLT-registered sender and template, so those IDs are passed when set;
 * each message kind has its own template ID. With SMS off (local runs), the message is logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpaAlertSms {
    public enum Kind { NEW, REMINDER, CANCELLED, MOVED }

    private final SnsClient sns;
    private final SmsProperties sms;

    @Value("${spa-alerts.sms.sender-id:}")
    private String senderId;
    @Value("${spa-alerts.sms.dlt-entity-id:}")
    private String entityId;
    @Value("${spa-alerts.sms.templates.new:}")
    private String newTemplate;
    @Value("${spa-alerts.sms.templates.reminder:}")
    private String reminderTemplate;
    @Value("${spa-alerts.sms.templates.cancelled:}")
    private String cancelledTemplate;
    @Value("${spa-alerts.sms.templates.moved:}")
    private String movedTemplate;
    /** Local runs only: write messages (with their links) to the log while SMS is off. */
    @Value("${spa-alerts.log-messages:false}")
    private boolean logMessages;

    /** An Indian mobile number in +91 form, or empty for a landline, a foreign or a malformed number. */
    public static Optional<String> mobile(String phone) {
        if (phone == null) return Optional.empty();
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("91") && digits.length() == 12) digits = digits.substring(2);
        else if (digits.startsWith("0") && digits.length() == 11) digits = digits.substring(1);
        return digits.matches("[6-9]\\d{9}") ? Optional.of("+91" + digits) : Optional.empty();
    }

    public void send(String to, Kind kind, String message) {
        if (!sms.isEnabled()) {
            if (logMessages) log.info("Spa SMS ({}) not sent, SMS is off. To {}: {}", kind, to, message);
            return;
        }
        if (sms.getProvider() != null && !"AWS_SNS".equalsIgnoreCase(sms.getProvider())) {
            // Booking alerts have their own DLT templates on AWS SNS; another OTP provider doesn't send them.
            log.info("Spa SMS ({}) to {} not sent: booking alerts need AWS SNS, and SMS provider is {}", kind, to, sms.getProvider());
            return;
        }
        Map<String, MessageAttributeValue> attributes = new HashMap<>();
        attributes.put("AWS.SNS.SMS.SMSType", text("Transactional"));
        if (StringUtils.hasText(senderId)) attributes.put("AWS.SNS.SMS.SenderID", text(senderId));
        if (StringUtils.hasText(entityId)) attributes.put("AWS.MM.SMS.EntityId", text(entityId));
        String template = switch (kind) {
            case NEW -> newTemplate;
            case REMINDER -> reminderTemplate;
            case CANCELLED -> cancelledTemplate;
            case MOVED -> movedTemplate;
        };
        if (StringUtils.hasText(template)) attributes.put("AWS.MM.SMS.TemplateId", text(template));
        sns.publish(PublishRequest.builder().phoneNumber(to).message(message).messageAttributes(attributes).build());
    }

    private static MessageAttributeValue text(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }
}
