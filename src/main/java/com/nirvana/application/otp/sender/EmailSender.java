package com.nirvana.application.otp.sender;

public interface EmailSender {
    void sendOtpEmail(String toEmail, String subject, String body);
}
