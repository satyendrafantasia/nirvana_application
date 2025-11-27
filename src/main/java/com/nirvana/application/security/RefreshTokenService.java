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

    @Transactional
    public RefreshToken issue(User user, String deviceFingerprint, String userAgent, String ipAddress) {
        OffsetDateTime now = OffsetDateTime.now();
        RefreshToken token = RefreshToken.builder()
                .user(user)
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
        RefreshToken next = issue(existing.getUser(), deviceFingerprint, existing.getUserAgent(), existing.getIpAddress());
        existing.setReplacedByToken(next.getToken());
        refreshTokenRepository.save(existing);
        return next;
    }

    @Transactional
    public void revoke(String refreshTokenValue, String deviceFingerprint) {
        refreshTokenRepository.findByTokenAndRevokedAtIsNull(refreshTokenValue)
                .ifPresent(token -> {
                    validateToken(token, deviceFingerprint);
                    token.setRevokedAt(OffsetDateTime.now());
                    refreshTokenRepository.save(token);
                });
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
