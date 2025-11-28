package com.nirvana.application.otp;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class OtpGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateNumericOtp(int length) {
        int bound = (int) Math.pow(10, length);
        int number = secureRandom.nextInt(bound);
        return String.format("%0" + length + "d", number);
    }
}
