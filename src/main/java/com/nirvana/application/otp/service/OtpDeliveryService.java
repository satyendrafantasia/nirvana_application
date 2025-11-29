package com.nirvana.application.otp.service;

import com.nirvana.application.config.EmailProperties;
import com.nirvana.application.config.OtpProperties;
import com.nirvana.application.config.SmsProperties;
import com.nirvana.application.otp.OtpGenerator;
import com.nirvana.application.otp.OtpPurpose;
import com.nirvana.application.otp.OtpVerification;
import com.nirvana.application.otp.OtpVerificationRepository;
import com.nirvana.application.otp.dto.OtpSendResponse;
import com.nirvana.application.otp.dto.OtpVerifyResponse;
import com.nirvana.application.otp.exception.InvalidOtpException;
import com.nirvana.application.otp.exception.MaxAttemptsExceededException;
import com.nirvana.application.otp.exception.OtpExpiredException;
import com.nirvana.application.otp.exception.OtpNotFoundException;
import com.nirvana.application.otp.exception.RateLimitExceededException;
import com.nirvana.application.otp.sender.EmailSender;
import com.nirvana.application.otp.sender.SmsSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service("otpDeliveryService")
@RequiredArgsConstructor
public class OtpDeliveryService {

    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(10);
    private static final int RATE_LIMIT_THRESHOLD = 5;

    private final OtpVerificationRepository otpVerificationRepository;
    private final OtpGenerator otpGenerator;
    private final SmsSender smsSender;
    private final EmailSender emailSender;
    private final OtpProperties otpProperties;
    private final SmsProperties smsProperties;
    private final EmailProperties emailProperties;

    @Transactional
    public OtpSendResponse sendOtp(String phoneNumber, String email) {
        String normalizedPhone = normalize(phoneNumber);
        String normalizedEmail = normalizeEmail(email);
        if (normalizedPhone == null && normalizedEmail == null) {
            throw new InvalidOtpException("Provide at least phoneNumber or email");
        }

        enforceRateLimits(normalizedPhone, normalizedEmail);

        String otpCode = otpGenerator.generateNumericOtp(otpProperties.getLength());
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(otpProperties.getExpiryMinutes());

        OtpVerification verification = OtpVerification.builder()
                .verificationId(UUID.randomUUID().toString())
                .phoneNumber(normalizedPhone)
                .email(normalizedEmail)
                .otpCode(otpCode)
                .expiresAt(expiresAt)
                .attempts(0)
                .maxAttempts(otpProperties.getMaxAttempts())
                .verified(false)
                .purpose(OtpPurpose.GENERIC)
                .registrationConsumed(false)
                .loginConsumed(false)
                .passwordResetConsumed(false)
                .build();

        otpVerificationRepository.save(verification);

        String message = String.format("Your OTP is %s. It is valid for %d minutes.", otpCode, otpProperties.getExpiryMinutes());
        if (normalizedPhone != null && smsProperties.isEnabled()) {
            smsSender.sendOtpSms(normalizedPhone, message);
        }
        if (normalizedEmail != null && emailProperties.isEnabled()) {
            emailSender.sendOtpEmail(normalizedEmail, "Your OTP Code", message);
        }

        return new OtpSendResponse(verification.getVerificationId());
    }

    @Transactional
    public OtpVerifyResponse verifyOtp(String verificationId, String otp) {
        OtpVerification verification = otpVerificationRepository.findByVerificationId(verificationId)
                .orElseThrow(() -> new OtpNotFoundException("Verification session not found"));

        if (Boolean.TRUE.equals(verification.getVerified())) {
            return new OtpVerifyResponse("VERIFIED");
        }
        if (verification.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new OtpExpiredException("OTP has expired");
        }
        if (verification.getAttempts() >= verification.getMaxAttempts()) {
            throw new MaxAttemptsExceededException("Maximum OTP attempts exceeded");
        }
        if (!verification.getOtpCode().equals(otp)) {
            verification.setAttempts(verification.getAttempts() + 1);
            otpVerificationRepository.save(verification);
            throw new InvalidOtpException("Invalid OTP");
        }

        verification.setVerified(true);
        otpVerificationRepository.save(verification);
        return new OtpVerifyResponse("VERIFIED");
    }

    private void enforceRateLimits(String phoneNumber, String email) {
        OffsetDateTime windowStart = OffsetDateTime.now().minus(RATE_LIMIT_WINDOW);
        if (phoneNumber != null) {
            long recent = otpVerificationRepository.countByPhoneNumberAndCreatedAtAfter(phoneNumber, windowStart);
            if (recent >= RATE_LIMIT_THRESHOLD) {
                throw new RateLimitExceededException("Too many OTP requests for this phone number");
            }
        }
        if (email != null) {
            long recent = otpVerificationRepository.countByEmailAndCreatedAtAfter(email, windowStart);
            if (recent >= RATE_LIMIT_THRESHOLD) {
                throw new RateLimitExceededException("Too many OTP requests for this email address");
            }
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase();
    }
}
