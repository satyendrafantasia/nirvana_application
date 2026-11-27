package com.nirvana.application.security;

import com.nirvana.application.model.RefreshToken;
import com.nirvana.application.model.User;
import com.nirvana.application.repository.RefreshTokenRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final com.nirvana.application.service.erasure.AccountErasureLog erasureLog;

    @Transactional
    public RefreshToken issue(User user, String deviceFingerprint, String userAgent, String ipAddress) {
        return issue(user, deviceFingerprint, userAgent, ipAddress, false);
    }

    @Transactional
    public RefreshToken issue(User user, String deviceFingerprint, String userAgent, String ipAddress, boolean mfaVerified) {
        if (SessionAssurance.requiresMfa(user) && !mfaVerified)
            throw new org.springframework.security.authentication.InsufficientAuthenticationException("Complete multifactor sign-in");
        if (!Boolean.TRUE.equals(user.getIsActive()) || user.getDataErasedAt() != null)
            throw new org.springframework.security.authentication.DisabledException("This account has been deleted");
        if (user.getDataErasureRequestedAt() != null) {
            // Every sign-in method ends here, so signing in during the grace period keeps the account.
            user.setDataErasureRequestedAt(null);
            erasureLog.cancelOnSignIn(user.getId());
        }
        OffsetDateTime now = OffsetDateTime.now();
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .mfaVerified(mfaVerified)
                .sessionFamily(UUID.randomUUID().toString())
                .token(UUID.randomUUID().toString())
                .deviceFingerprint(hashFingerprint(deviceFingerprint))
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .createdAt(now)
                .expiresAt(now.plusDays(jwtProperties.getRefreshExpirationDays()))
                .build();
        return refreshTokenRepository.save(token);
    }

    @Transactional
    public RefreshToken rotate(String refreshTokenValue, String deviceFingerprint) {
        RefreshToken existing = refreshTokenRepository.findByTokenAndRevokedAtIsNull(refreshTokenValue)
                .orElseThrow(() -> new EntityNotFoundException("Refresh token not found"));

        validateToken(existing, deviceFingerprint);

        existing.setRevokedAt(OffsetDateTime.now());
        RefreshToken next = issue(existing.getUser(), deviceFingerprint, existing.getUserAgent(), existing.getIpAddress(), existing.isMfaVerified());
        if (existing.getSessionFamily() != null) next.setSessionFamily(existing.getSessionFamily());
        existing.setReplacedByToken(next.getToken());
        refreshTokenRepository.save(existing);
        return next;
    }

    @Transactional
    public void revoke(String refreshTokenValue, String deviceFingerprint) {
        refreshTokenRepository.findByToken(refreshTokenValue)
                .ifPresent(token -> {
                    OffsetDateTime now = OffsetDateTime.now();
                    if (!token.getExpiresAt().isAfter(now)) return;
                    validateToken(token, deviceFingerprint);
                    // Another browser tab may already have rotated the cookie sent with logout.
                    if (token.getSessionFamily() != null) {
                        refreshTokenRepository.revokeFamily(token.getUser().getId(), token.getSessionFamily(), now);
                    } else if (token.getRevokedAt() == null) {
                        token.setRevokedAt(now);
                    }
                });
    }

    /** Ends every session of the user, on every device. */
    @Transactional
    public int revokeAll(Long userId) {
        return refreshTokenRepository.revokeAllForUser(userId, OffsetDateTime.now());
    }

    @Transactional
    public void revokeDeviceSessions(Long userId, String deviceFingerprint) {
        if (deviceFingerprint == null || deviceFingerprint.isBlank()) {
            return;
        }
        refreshTokenRepository.deleteByUserIdAndDeviceFingerprintAndExpiresAtAfter(
                userId, hashFingerprint(deviceFingerprint), OffsetDateTime.now());
    }

    private void validateToken(RefreshToken token, String deviceFingerprint) {
        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalStateException("Refresh token expired");
        }
        String hashedProvided = hashFingerprint(deviceFingerprint);
        if (token.getDeviceFingerprint() != null && deviceFingerprint != null
                && !token.getDeviceFingerprint().equals(hashedProvided)) {
            throw new IllegalArgumentException("Device fingerprint mismatch");
        }
    }

    private String hashFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(fingerprint.getBytes());
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
