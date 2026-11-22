package com.nirvana.application;

import com.nirvana.application.config.SmsProperties;
import com.nirvana.application.otp.sender.SnsSmsSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** OTP texts reach Indian phones only when they carry the DLT sender, entity and template IDs. */
class OtpSmsDltTest {

    private static SnsSmsSender sender(SnsClient sns, boolean enabled, String senderId, String entityId, String template) {
        SmsProperties props = new SmsProperties();
        props.setEnabled(enabled);
        SnsSmsSender sender = new SnsSmsSender(sns, props);
        ReflectionTestUtils.setField(sender, "senderId", senderId);
        ReflectionTestUtils.setField(sender, "entityId", entityId);
        ReflectionTestUtils.setField(sender, "otpTemplateId", template);
        return sender;
    }

    private static PublishRequest sent(SnsClient sns) {
        ArgumentCaptor<PublishRequest> request = ArgumentCaptor.forClass(PublishRequest.class);
        verify(sns).publish(request.capture());
        return request.getValue();
    }

    @Test void sendsTheDltIdsAsATransactionalMessage() {
        SnsClient sns = mock(SnsClient.class);
        sender(sns, true, "NRVANA", "1201000000000000001", "1207000000000000002")
                .sendOtpSms("+919876543210", "Your OTP is 123456. It is valid for 10 minutes.");
        var attributes = sent(sns).messageAttributes();
        assertEquals("Transactional", attributes.get("AWS.SNS.SMS.SMSType").stringValue());
        assertEquals("NRVANA", attributes.get("AWS.SNS.SMS.SenderID").stringValue());
        assertEquals("1201000000000000001", attributes.get("AWS.MM.SMS.EntityId").stringValue());
        assertEquals("1207000000000000002", attributes.get("AWS.MM.SMS.TemplateId").stringValue());
    }

    @Test void leavesOutIdsThatAreNotSet() {
        SnsClient sns = mock(SnsClient.class);
        sender(sns, true, "", "", "").sendOtpSms("+919876543210", "Your OTP is 123456. It is valid for 10 minutes.");
        var attributes = sent(sns).messageAttributes();
        assertEquals("Transactional", attributes.get("AWS.SNS.SMS.SMSType").stringValue());
        assertFalse(attributes.containsKey("AWS.SNS.SMS.SenderID"));
        assertFalse(attributes.containsKey("AWS.MM.SMS.TemplateId"));
    }

    @Test void sendsNothingWhileSmsIsOff() {
        SnsClient sns = mock(SnsClient.class);
        sender(sns, false, "NRVANA", "1", "2").sendOtpSms("+919876543210", "Your OTP is 123456.");
        verifyNoInteractions(sns);
    }
}
