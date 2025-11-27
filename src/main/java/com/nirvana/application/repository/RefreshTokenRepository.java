package com.nirvana.application.repository;

import com.nirvana.application.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenAndRevokedAtIsNull(String token);

    long deleteByUserIdAndDeviceFingerprintAndExpiresAtAfter(Long userId, String deviceFingerprint, OffsetDateTime now);
}
