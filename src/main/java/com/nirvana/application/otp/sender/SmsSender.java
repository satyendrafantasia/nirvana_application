package com.nirvana.application.otp.sender;

public interface SmsSender {
    void sendOtpSms(String phoneNumber, String message);
}
