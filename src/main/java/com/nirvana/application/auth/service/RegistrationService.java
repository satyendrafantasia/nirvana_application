package com.nirvana.application.auth.service;

import com.nirvana.application.auth.dto.CompleteRegistrationRequest;
import com.nirvana.application.auth.dto.RegisteredUserDto;
import com.nirvana.application.auth.dto.RegistrationAuthResponse;
import com.nirvana.application.auth.exception.RegistrationException;
import com.nirvana.application.model.RefreshToken;
import com.nirvana.application.model.Role;
import com.nirvana.application.model.User;
import com.nirvana.application.model.enums.RoleType;
import com.nirvana.application.otp.OtpVerification;
import com.nirvana.application.otp.OtpVerificationRepository;
import com.nirvana.application.repository.RoleRepository;
import com.nirvana.application.repository.UserRepository;
import com.nirvana.application.security.DeviceFingerprintResolver;
import com.nirvana.application.security.JwtProperties;
import com.nirvana.application.security.JwtTokenService;
import com.nirvana.application.security.RefreshTokenService;
import com.nirvana.application.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final OtpVerificationRepository otpVerificationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final DeviceFingerprintResolver deviceFingerprintResolver;
    private final PasswordEncoder passwordEncoder;
    private final JwtProperties jwtProperties;

    @Transactional
    public RegistrationAuthResponse completeRegistration(CompleteRegistrationRequest request) {
        OtpVerification verification = otpVerificationRepository.findByRegistrationToken(request.getRegistrationToken())
                .orElseThrow(() -> new RegistrationException("INVALID_REGISTRATION_TOKEN", "Invalid registration token", HttpStatus.BAD_REQUEST));

        validateVerification(verification);
        enforceUniqueConstraints(verification);

        Role role = Optional.ofNullable(roleRepository.findByRoleType(RoleType.CUSTOMER))
                .orElseThrow(() -> new RegistrationException("ROLE_NOT_CONFIGURED", "Default role not configured", HttpStatus.INTERNAL_SERVER_ERROR));

        User user = User.builder()
                .username(resolveUsername(verification))
                .email(verification.getEmail())
                .phone(verification.getPhoneNumber())
                .name(request.getFullName())
                .displayName(request.getFullName())
                .role(role)
                .roles(Set.of(Roles.USER))
                .password(passwordEncoder.encode(request.getPassword()))
                .active(true)
                .isActive(true)
                .emailVerified(verification.getEmail() != null)
                .phoneVerified(verification.getPhoneNumber() != null)
                .build();

        user = userRepository.save(user);

        verification.setRegistrationConsumed(true);
        otpVerificationRepository.save(verification);

        String fingerprint = deviceFingerprintResolver.resolveFingerprint();
        RefreshToken refreshToken = refreshTokenService.issue(user, fingerprint, resolveUserAgent(), resolveIp());
        String accessToken = jwtTokenService.generateToken(user, fingerprint);

        return RegistrationAuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(Duration.ofMinutes(jwtProperties.getExpirationMinutes()).toSeconds())
                .user(RegisteredUserDto.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .phoneNumber(user.getPhone())
                        .fullName(user.getName())
                        .build())
                .build();
    }

    private void validateVerification(OtpVerification verification) {
        if (!Boolean.TRUE.equals(verification.getVerified())) {
            throw new RegistrationException("OTP_NOT_VERIFIED", "OTP verification is required before completing registration", HttpStatus.BAD_REQUEST);
        }
        if (Boolean.TRUE.equals(verification.getRegistrationConsumed())) {
            throw new RegistrationException("REGISTRATION_TOKEN_CONSUMED", "Registration token already used", HttpStatus.BAD_REQUEST);
        }
        if (verification.getRegistrationTokenExpiresAt() != null && verification.getRegistrationTokenExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new RegistrationException("REGISTRATION_TOKEN_EXPIRED", "Registration token has expired", HttpStatus.BAD_REQUEST);
        }
    }

    private void enforceUniqueConstraints(OtpVerification verification) {
        if (verification.getPhoneNumber() != null && userRepository.existsByPhone(verification.getPhoneNumber())) {
            throw new RegistrationException("USER_ALREADY_EXISTS", "User already exists with this phone number", HttpStatus.CONFLICT);
        }
        if (verification.getEmail() != null && userRepository.existsByEmail(verification.getEmail())) {
            throw new RegistrationException("USER_ALREADY_EXISTS", "User already exists with this email", HttpStatus.CONFLICT);
        }
    }

    private String resolveUsername(OtpVerification verification) {
        if (verification.getEmail() != null && !verification.getEmail().isBlank()) {
            return verification.getEmail();
        }
        return verification.getPhoneNumber();
    }

    private String resolveUserAgent() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(attrs -> attrs.getRequest().getHeader("User-Agent"))
                .orElse(null);
    }

    private String resolveIp() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(attrs -> {
                    String forwarded = attrs.getRequest().getHeader("X-Forwarded-For");
                    if (forwarded != null && !forwarded.isBlank()) {
                        return forwarded.split(",")[0];
                    }
                    return attrs.getRequest().getRemoteAddr();
                })
                .orElse(null);
    }
}
