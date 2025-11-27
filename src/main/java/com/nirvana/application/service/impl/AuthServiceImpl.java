package com.nirvana.application.service.impl;

import com.nirvana.application.model.Role;
import com.nirvana.application.model.User;
import com.nirvana.application.model.dto.AuthResponse;
import com.nirvana.application.model.dto.LoginRequest;
import com.nirvana.application.model.dto.LogoutRequest;
import com.nirvana.application.model.dto.RefreshTokenRequest;
import com.nirvana.application.model.dto.UserRegistrationDTO;
import com.nirvana.application.model.enums.RoleType;
import com.nirvana.application.repository.RoleRepository;
import com.nirvana.application.repository.UserRepository;
import com.nirvana.application.security.JwtTokenService;
import com.nirvana.application.security.DeviceFingerprintResolver;
import com.nirvana.application.security.RefreshTokenService;
import com.nirvana.application.security.UserPrincipal;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.AuthService;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

import static com.nirvana.application.security.Roles.PLATFORM_ADMIN;
import static com.nirvana.application.security.Roles.SPA_OWNER;
import static com.nirvana.application.security.Roles.USER;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final DeviceFingerprintResolver deviceFingerprintResolver;

    @Override
    @Transactional
    public AuthResponse register(UserRegistrationDTO request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new EntityExistsException("Username already exists");
        }

        if (!Boolean.TRUE.equals(request.getAcceptPrivacyPolicy())) {
            throw new IllegalArgumentException("User must accept privacy policy to register");
        }

        RoleType roleType = Optional.ofNullable(request.getRoleType()).orElse(RoleType.CUSTOMER);
        Role role = Optional.ofNullable(roleRepository.findByRoleType(roleType))
                .orElseThrow(() -> new EntityNotFoundException("Role not configured: " + roleType));

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getUsername())
                .name(request.getName())
                .lastName(request.getLastName())
                .role(role)
                .roles(resolveRoleNames(roleType))
                .password(passwordEncoder.encode(request.getPassword()))
                .active(true)
                .isActive(true)
                .timezone(Optional.ofNullable(request.getTimezone()).orElse("UTC"))
                .locale(request.getLocale())
                .marketingOptIn(Optional.ofNullable(request.getMarketingOptIn()).orElse(Boolean.TRUE))
                .privacyConsentVersion(Optional.ofNullable(request.getConsentVersion()).orElse("v1"))
                .consentSource(request.getConsentSource())
                .privacyConsentedAt(OffsetDateTime.now())
                .build();

        User saved = userRepository.save(user);
        return buildAuthResponse(saved);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + principal.getId()));

        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);

        return buildAuthResponse(user);
    }

    @Override
    public AuthResponse refresh(RefreshTokenRequest request) {
        String fingerprint = deviceFingerprintResolver.resolveFingerprint();
        var rotated = refreshTokenService.rotate(request.getRefreshToken(),
                request.getDeviceFingerprint() != null ? request.getDeviceFingerprint() : fingerprint);
        User user = rotated.getUser();
        return buildAuthResponse(user, rotated.getToken(), request.getDeviceFingerprint());
    }

    @Override
    public void logout(LogoutRequest request) {
        String fingerprint = request.getDeviceFingerprint();
        if (fingerprint == null) {
            fingerprint = deviceFingerprintResolver.resolveFingerprint();
        }
        if (request.getRefreshToken() != null) {
            refreshTokenService.revoke(request.getRefreshToken(), fingerprint);
        }
        Long currentUserId = null;
        try {
            currentUserId = SecurityUtils.getCurrentUserId();
        } catch (Exception ignored) {
        }
        if (currentUserId != null) {
            refreshTokenService.revokeDeviceSessions(currentUserId, fingerprint);
        }
    }

    private AuthResponse buildAuthResponse(User user) {
        String fingerprint = deviceFingerprintResolver.resolveFingerprint();
        var refreshToken = refreshTokenService.issue(user, fingerprint, resolveUserAgent(), resolveIp());
        return buildAuthResponse(user, refreshToken.getToken(), fingerprint);
    }

    private AuthResponse buildAuthResponse(User user, String refreshToken, String fingerprint) {
        String token = jwtTokenService.generateToken(user, fingerprint);
        return AuthResponse.builder()
                .accessToken(token)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .roles(user.getRoles())
                .mfaRequired(Boolean.TRUE.equals(user.getMfaEnabled()))
                .build();
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

    private Set<String> resolveRoleNames(RoleType roleType) {
        return switch (roleType) {
            case ADMIN -> Set.of(PLATFORM_ADMIN);
            case Spa_MANAGER -> Set.of(SPA_OWNER);
            case CUSTOMER -> Set.of(USER);
        };
    }
}
