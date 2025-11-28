package com.nirvana.application.auth.service;

import com.nirvana.application.auth.dto.OtpRequestDto;
import com.nirvana.application.auth.dto.RegistrationTokenResponse;
import com.nirvana.application.auth.dto.VerificationResponse;
import com.nirvana.application.auth.dto.VerifyOtpRequest;
import com.nirvana.application.auth.exception.RegistrationException;
import com.nirvana.application.otp.OtpVerification;
import com.nirvana.application.otp.OtpVerificationRepository;
import com.nirvana.application.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service("registrationOtpService")
@RequiredArgsConstructor
public class OtpService {

    private static final int OTP_LENGTH = 6;
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final Duration OTP_REQUEST_WINDOW = Duration.ofMinutes(10);
    private static final int MAX_REQUESTS_PER_WINDOW = 5;

    private final OtpVerificationRepository otpVerificationRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public VerificationResponse requestOtp(OtpRequestDto request) {
        String phone = normalize(request.getPhoneNumber());
        String email = normalizeEmail(request.getEmail());
        if (phone == null && email == null) {
            throw new RegistrationException("INVALID_CONTACT", "Provide at least phoneNumber or email", HttpStatus.BAD_REQUEST);
        }

        enforceExistingUserChecks(phone, email);
        enforceRateLimit(phone, email);

        String otp = generateOtpCode();
        OtpVerification verification = OtpVerification.builder()
                .verificationId(UUID.randomUUID().toString())
                .phoneNumber(phone)
                .email(email)
                .otpCode(otp)
                .expiresAt(OffsetDateTime.now().plus(OTP_TTL))
                .attempts(0)
                .maxAttempts(MAX_ATTEMPTS)
                .verified(false)
                .registrationConsumed(false)
                .build();

        otpVerificationRepository.save(verification);

        log.info("OTP {} generated for verification {}", otp, verification.getVerificationId());
        // TODO: Integrate with SMS/email provider

        return VerificationResponse.builder()
                .verificationId(verification.getVerificationId())
                .build();
    }

    @Transactional
    public RegistrationTokenResponse verifyOtp(VerifyOtpRequest request) {
        OtpVerification verification = otpVerificationRepository.findByVerificationId(request.getVerificationId())
                .orElseThrow(() -> new RegistrationException("INVALID_VERIFICATION_ID", "Verification session not found", HttpStatus.BAD_REQUEST));

        if (Boolean.TRUE.equals(verification.getRegistrationConsumed())) {
            throw new RegistrationException("REGISTRATION_TOKEN_CONSUMED", "Registration already completed", HttpStatus.BAD_REQUEST);
        }
        if (verification.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new RegistrationException("OTP_EXPIRED", "OTP has expired", HttpStatus.BAD_REQUEST);
        }
        if (Boolean.TRUE.equals(verification.getVerified()) && verification.getRegistrationToken() != null
                && verification.getRegistrationTokenExpiresAt() != null
                && verification.getRegistrationTokenExpiresAt().isAfter(OffsetDateTime.now())) {
            return RegistrationTokenResponse.builder().registrationToken(verification.getRegistrationToken()).build();
        }
        if (verification.getAttempts() >= verification.getMaxAttempts()) {
            throw new RegistrationException("MAX_ATTEMPTS_EXCEEDED", "Maximum OTP attempts exceeded", HttpStatus.BAD_REQUEST);
        }

        verification.setAttempts(verification.getAttempts() + 1);

        if (!verification.getOtpCode().equals(request.getOtp())) {
            otpVerificationRepository.save(verification);
            String code = verification.getAttempts() >= verification.getMaxAttempts()
                    ? "MAX_ATTEMPTS_EXCEEDED"
                    : "INVALID_OTP";
            throw new RegistrationException(code, "Invalid OTP", HttpStatus.BAD_REQUEST,
                    Map.of("attempts", verification.getAttempts(), "maxAttempts", verification.getMaxAttempts()));
        }

        verification.setVerified(true);
        verification.setRegistrationToken(UUID.randomUUID().toString());
        verification.setRegistrationTokenExpiresAt(OffsetDateTime.now().plusMinutes(15));
        otpVerificationRepository.save(verification);

        return RegistrationTokenResponse.builder()
                .registrationToken(verification.getRegistrationToken())
                .build();
    }

    private void enforceExistingUserChecks(String phone, String email) {
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new RegistrationException("USER_ALREADY_EXISTS", "User already exists with this phone number", HttpStatus.CONFLICT);
        }
        if (email != null && userRepository.existsByEmail(email)) {
            throw new RegistrationException("USER_ALREADY_EXISTS", "User already exists with this email", HttpStatus.CONFLICT);
        }
    }

    private void enforceRateLimit(String phone, String email) {
        OffsetDateTime windowStart = OffsetDateTime.now().minus(OTP_REQUEST_WINDOW);
        if (phone != null) {
            long recentPhoneRequests = otpVerificationRepository.countByPhoneNumberAndCreatedAtAfter(phone, windowStart);
            if (recentPhoneRequests >= MAX_REQUESTS_PER_WINDOW) {
                throw new RegistrationException("OTP_RATE_LIMITED", "Too many OTP requests for this phone number", HttpStatus.TOO_MANY_REQUESTS);
            }
        }
        if (email != null) {
            long recentEmailRequests = otpVerificationRepository.countByEmailAndCreatedAtAfter(email, windowStart);
            if (recentEmailRequests >= MAX_REQUESTS_PER_WINDOW) {
                throw new RegistrationException("OTP_RATE_LIMITED", "Too many OTP requests for this email", HttpStatus.TOO_MANY_REQUESTS);
            }
        }
    }

    private String generateOtpCode() {
        int bound = (int) Math.pow(10, OTP_LENGTH);
        int number = secureRandom.nextInt(bound);
        return String.format("%0" + OTP_LENGTH + "d", number);
    }

    private String normalize(String input) {
        return input == null || input.isBlank() ? null : input.trim();
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase();
    }
}
