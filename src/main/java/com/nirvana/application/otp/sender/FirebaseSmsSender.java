package com.nirvana.application.otp.sender;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * With Firebase, the browser asks Google to text the code and Google checks it, so the server sends
 * nothing. Verify endpoints then take Google's signed proof of the phone (see FirebasePhoneTokens).
 */
@Component
@ConditionalOnProperty(name = "sms.provider", havingValue = "FIREBASE")
public class FirebaseSmsSender implements SmsSender {
    @Override
    public void sendOtpSms(String phoneNumber, String message) {
        // Nothing to send: Firebase delivers the code.
    }
}
