package com.nirvana.application.auth.service;

import com.nirvana.application.auth.dto.CompletePasswordResetRequest;
import com.nirvana.application.auth.dto.OtpLoginCompleteRequest;
import com.nirvana.application.auth.dto.OtpLoginTokenResponse;
import com.nirvana.application.auth.dto.OtpRequestDto;
import com.nirvana.application.auth.dto.PasswordResetTokenResponse;
import com.nirvana.application.auth.dto.VerificationResponse;
import com.nirvana.application.auth.dto.VerifyOtpRequest;
import com.nirvana.application.model.User;
import com.nirvana.application.model.dto.AuthResponse;
import com.nirvana.application.otp.OtpPurpose;
import com.nirvana.application.otp.OtpVerification;
import com.nirvana.application.otp.OtpVerificationRepository;
import com.nirvana.application.otp.sender.EmailSender;
import com.nirvana.application.otp.sender.SmsSender;
import com.nirvana.application.repository.UserRepository;
import com.nirvana.application.config.EmailProperties;
import com.nirvana.application.config.SmsProperties;
import com.nirvana.application.security.DeviceFingerprintResolver;
import com.nirvana.application.security.JwtTokenService;
import com.nirvana.application.security.RefreshTokenService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpAuthService {

    private static final int OTP_LENGTH = 6;
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final Duration OTP_REQUEST_WINDOW = Duration.ofMinutes(10);
    private static final int MAX_REQUESTS_PER_WINDOW = 5;
    private static final Duration LOGIN_TOKEN_TTL = Duration.ofMinutes(15);

    private final OtpVerificationRepository otpVerificationRepository;
    private final UserRepository userRepository;
    private final SmsSender smsSender;
    private final EmailSender emailSender;
    private final SmsProperties smsProperties;
    private final com.nirvana.application.otp.FirebasePhoneTokens phoneTokens;
    private final EmailProperties emailProperties;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final DeviceFingerprintResolver deviceFingerprintResolver;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public VerificationResponse requestLoginOtp(OtpRequestDto request) {
        User user = resolveExistingUser(request);
        String phone = normalize(request.getPhoneNumber());
        String email = normalizeEmail(request.getEmail());
        ensureContactMatchesUser(user, phone, email);
        enforceRateLimit(phone != null ? phone : user.getPhone(), email != null ? email : user.getEmail());
        OtpVerification verification = createAndSendOtp(user, phone, email, OtpPurpose.LOGIN);
        return new VerificationResponse(verification.getVerificationId());
    }

    @Transactional(noRollbackFor = InvalidCodeException.class)
    public OtpLoginTokenResponse verifyLoginOtp(VerifyOtpRequest request) {
        OtpVerification verification = loadForPurpose(request.getVerificationId(), OtpPurpose.LOGIN);
        validateOtp(verification, request);
        verification.setVerified(true);
        verification.setLoginToken(UUID.randomUUID().toString());
        verification.setLoginTokenExpiresAt(OffsetDateTime.now().plus(LOGIN_TOKEN_TTL));
        otpVerificationRepository.save(verification);
        return new OtpLoginTokenResponse(verification.getLoginToken());
    }

    @Transactional
    public AuthResponse completeOtpLogin(OtpLoginCompleteRequest request) {
        User user = consumeLoginProof(request.getLoginToken());
        com.nirvana.application.security.SessionAssurance.requirePasswordSignIn(user);
        return issueAuthResponse(user, false);
    }

    @Transactional
    public User consumeLoginProof(String loginToken) {
        OtpVerification verification = otpVerificationRepository.findByLoginToken(loginToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid login token"));
        if (!OtpPurpose.LOGIN.equals(verification.getPurpose())) {
            throw new IllegalArgumentException("Login token does not belong to an OTP login session");
        }
        if (Boolean.TRUE.equals(verification.getLoginConsumed())) {
            throw new IllegalStateException("Login token already used");
        }
        if (verification.getLoginTokenExpiresAt() == null || verification.getLoginTokenExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalStateException("Login token expired");
        }
        User user = resolveUserFromVerification(verification);
        verification.setLoginConsumed(true);
        otpVerificationRepository.save(verification);
        return user;
    }

    @Transactional
    public VerificationResponse requestPasswordResetOtp(OtpRequestDto request) {
        User user = resolveExistingUser(request);
        ensureSelfServiceRecovery(user);
        String phone = normalize(request.getPhoneNumber());
        String email = normalizeEmail(request.getEmail());
        ensureContactMatchesUser(user, phone, email);
        enforceRateLimit(phone != null ? phone : user.getPhone(), email != null ? email : user.getEmail());
        OtpVerification verification = createAndSendOtp(user, phone, email, OtpPurpose.PASSWORD_RESET);
        return new VerificationResponse(verification.getVerificationId());
    }

    @Transactional(noRollbackFor = InvalidCodeException.class)
    public PasswordResetTokenResponse verifyPasswordResetOtp(VerifyOtpRequest request) {
        OtpVerification verification = loadForPurpose(request.getVerificationId(), OtpPurpose.PASSWORD_RESET);
        validateOtp(verification, request);
        verification.setVerified(true);
        verification.setPasswordResetToken(UUID.randomUUID().toString());
        verification.setPasswordResetTokenExpiresAt(OffsetDateTime.now().plus(LOGIN_TOKEN_TTL));
        otpVerificationRepository.save(verification);
        return new PasswordResetTokenResponse(verification.getPasswordResetToken());
    }

    @Transactional
    public void completePasswordReset(CompletePasswordResetRequest request) {
        OtpVerification verification = otpVerificationRepository.findByPasswordResetToken(request.getPasswordResetToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid password reset token"));
        if (!OtpPurpose.PASSWORD_RESET.equals(verification.getPurpose())) {
            throw new IllegalArgumentException("Token does not belong to a password reset session");
        }
        if (Boolean.TRUE.equals(verification.getPasswordResetConsumed())) {
            throw new IllegalStateException("Password reset token already used");
        }
        if (verification.getPasswordResetTokenExpiresAt() == null || verification.getPasswordResetTokenExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalStateException("Password reset token expired");
        }
        User user = resolveUserFromVerification(verification);
        ensureSelfServiceRecovery(user);
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        // A reset usually means the old password may be known to someone else: end every session.
        user.setCredentialsChangedAt(OffsetDateTime.now());
        userRepository.save(user);
        refreshTokenService.revokeAll(user.getId());
        verification.setPasswordResetConsumed(true);
        otpVerificationRepository.save(verification);
    }

    @Transactional
    public VerificationResponse startMfaChallenge(User user) {
        String phone = Boolean.TRUE.equals(user.getPhoneVerified()) && smsProperties.phoneCodesAvailable()
                ? normalize(user.getPhone()) : null;
        String email = Boolean.TRUE.equals(user.getEmailVerified()) && emailProperties.isEnabled()
                ? normalizeEmail(user.getEmail()) : null;
        if (phone == null && email == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "Your account needs a verified second factor. Contact support to enroll or recover it.");
        }
        enforceRateLimit(phone, email);
        // Deliver to one verified factor. Firebase phone challenges are issued by the browser.
        OtpVerification verification = createAndSendOtp(user, phone, phone == null ? email : null, OtpPurpose.MFA);
        VerificationResponse response = new VerificationResponse(verification.getVerificationId());
        response.setFirebasePhone(phone != null && smsProperties.isFirebase());
        return response;
    }

    @Transactional(noRollbackFor = InvalidCodeException.class)
    public AuthResponse completeMfa(VerifyOtpRequest request) {
        OtpVerification verification = loadForPurpose(request.getVerificationId(), OtpPurpose.MFA);
        validateOtp(verification, request);
        verification.setVerified(true);
        verification.setLoginConsumed(true);
        otpVerificationRepository.save(verification);
        User user = resolveUserFromVerification(verification);
        return issueAuthResponse(user, true);
    }

    private OtpVerification createAndSendOtp(User user, String phone, String email, OtpPurpose purpose) {
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
                .purpose(purpose)
                .userId(user != null ? user.getId() : null)
                .registrationConsumed(false)
                .loginConsumed(false)
                .passwordResetConsumed(false)
                .build();

        otpVerificationRepository.save(verification);
        deliverOtp(phone, email, otp);
        return verification;
    }

    private void deliverOtp(String phone, String email, String otp) {
        boolean smsEnabled = phone != null && smsProperties.phoneCodesAvailable();
        boolean emailEnabled = email != null && emailProperties.isEnabled();
        if (!smsEnabled && !emailEnabled) {
            throw new IllegalStateException("OTP delivery is disabled");
        }
        String message = String.format("Your OTP is %s. It is valid for %d minutes.", otp, OTP_TTL.toMinutes());
        try {
            if (smsEnabled) {
                smsSender.sendOtpSms(phone, message);
            }
            if (emailEnabled) {
                emailSender.sendOtpEmail(email, "Your OTP Code", message);
            }
        } catch (RuntimeException ex) {
            log.error("OTP delivery failed ({})", ex.getClass().getSimpleName());
            throw new IllegalStateException("Failed to deliver OTP");
        }
    }

    private OtpVerification loadForPurpose(String verificationId, OtpPurpose purpose) {
        OtpVerification verification = otpVerificationRepository.findByVerificationId(verificationId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification id"));
        if (!purpose.equals(verification.getPurpose())) {
            throw new IllegalArgumentException("Verification id is not for the requested flow");
        }
        return verification;
    }

    // Wrong proofs must commit the attempt counter even though the call returns a 400.
    private static final class InvalidCodeException extends IllegalArgumentException {
        private InvalidCodeException() { super("Invalid verification code"); }
    }

    private void validateOtp(OtpVerification verification, VerifyOtpRequest request) {
        if (Boolean.TRUE.equals(verification.getVerified())) {
            throw new IllegalStateException("This verification has already been used");
        }
        if (verification.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalStateException("OTP has expired");
        }
        if (verification.getAttempts() >= verification.getMaxAttempts()) {
            throw new IllegalStateException("Maximum OTP attempts exceeded");
        }
        verification.setAttempts(verification.getAttempts() + 1);
        if (request.getFirebaseIdToken() != null && !request.getFirebaseIdToken().isBlank()) {
            boolean proven;
            try {
                proven = smsProperties.isFirebase() && verification.getPhoneNumber() != null
                        && phoneTokens.proves(request.getFirebaseIdToken(), verification.getPhoneNumber());
            } catch (IllegalArgumentException invalid) {
                proven = false;
            }
            if (!proven) {
                otpVerificationRepository.save(verification);
                throw new InvalidCodeException();
            }
            return;
        }
        boolean firebasePhoneOnly = smsProperties.isFirebase() && verification.getPhoneNumber() != null
                && verification.getEmail() == null;
        if (firebasePhoneOnly || !verification.getOtpCode().equals(request.getOtp())) {
            otpVerificationRepository.save(verification);
            throw new InvalidCodeException();
        }
    }

    private User resolveExistingUser(OtpRequestDto request) {
        String phone = normalize(request.getPhoneNumber());
        String email = normalizeEmail(request.getEmail());
        if (phone == null && email == null) {
            throw new IllegalArgumentException("Provide at least phoneNumber or email");
        }
        Optional<User> user = phone != null
                ? userRepository.findByPhone(phone)
                : Optional.empty();
        if (user.isEmpty() && email != null) {
            user = userRepository.findByEmail(email);
        }
        return user.orElseThrow(() -> new EntityNotFoundException("User not found for provided contact"));
    }

    private void ensureContactMatchesUser(User user, String phone, String email) {
        if (phone != null && (user.getPhone() == null || !user.getPhone().equals(phone))) {
            throw new IllegalArgumentException("Phone number does not match user record");
        }
        if (email != null && (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(email))) {
            throw new IllegalArgumentException("Email does not match user record");
        }
    }

    private void enforceRateLimit(String phone, String email) {
        OffsetDateTime windowStart = OffsetDateTime.now().minus(OTP_REQUEST_WINDOW);
        if (phone != null) {
            long recentPhoneRequests = otpVerificationRepository.countByPhoneNumberAndCreatedAtAfter(phone, windowStart);
            if (recentPhoneRequests >= MAX_REQUESTS_PER_WINDOW) {
                throw new IllegalStateException("Too many OTP requests for this phone number");
            }
        }
        if (email != null) {
            long recentEmailRequests = otpVerificationRepository.countByEmailAndCreatedAtAfter(email, windowStart);
            if (recentEmailRequests >= MAX_REQUESTS_PER_WINDOW) {
                throw new IllegalStateException("Too many OTP requests for this email");
            }
        }
    }

    private void ensureSelfServiceRecovery(User user) {
        if (com.nirvana.application.security.SessionAssurance.requiresMfa(user))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "Contact support for verified account recovery.");
    }

    private String normalize(String input) {
        return input == null || input.isBlank() ? null : input.trim();
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase();
    }

    private String generateOtpCode() {
        int bound = (int) Math.pow(10, OTP_LENGTH);
        int number = secureRandom.nextInt(bound);
        return String.format("%0" + OTP_LENGTH + "d", number);
    }

    private AuthResponse issueAuthResponse(User user, boolean mfaVerified) {
        String fingerprint = deviceFingerprintResolver.resolveFingerprint();
        userRepository.recordSignIn(user.getId());
        var refreshToken = refreshTokenService.issue(user, fingerprint, resolveUserAgent(), resolveIp(), mfaVerified);
        String token = jwtTokenService.generateToken(user, fingerprint, mfaVerified, refreshToken.getSessionFamily());
        return AuthResponse.builder()
                .accessToken(token)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .roles(user.getRoles())
                .mfaRequired(false)
                .build();
    }

    private User resolveUserFromVerification(OtpVerification verification) {
        if (verification.getUserId() != null) {
            return userRepository.findById(verification.getUserId())
                    .orElseThrow(() -> new EntityNotFoundException("User not found"));
        }
        if (verification.getPhoneNumber() != null) {
            return userRepository.findByPhone(verification.getPhoneNumber())
                    .orElseThrow(() -> new EntityNotFoundException("User not found"));
        }
        if (verification.getEmail() != null) {
            return userRepository.findByEmail(verification.getEmail())
                    .orElseThrow(() -> new EntityNotFoundException("User not found"));
        }
        throw new EntityNotFoundException("User not linked to verification");
    }

    private String resolveUserAgent() {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest().getHeader("User-Agent");
        }
        return null;
    }

    private String resolveIp() {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servletAttrs) {
            String forwarded = servletAttrs.getRequest().getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0];
            }
            return servletAttrs.getRequest().getRemoteAddr();
        }
        return null;
    }
}
