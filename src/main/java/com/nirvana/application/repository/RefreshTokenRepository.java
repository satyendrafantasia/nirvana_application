package com.nirvana.application.repository;

import com.nirvana.application.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenAndRevokedAtIsNull(String token);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByToken(String token);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("""
            update RefreshToken t set t.revokedAt = :now
            where t.user.id = :userId and t.sessionFamily = :family and t.revokedAt is null
            """)
    int revokeFamily(@org.springframework.data.repository.query.Param("userId") Long userId,
                     @org.springframework.data.repository.query.Param("family") String family,
                     @org.springframework.data.repository.query.Param("now") OffsetDateTime now);

    boolean existsByUser_IdAndSessionFamilyAndRevokedAtIsNullAndExpiresAtAfter(
            Long userId, String sessionFamily, OffsetDateTime now);

    long deleteByUserIdAndDeviceFingerprintAndExpiresAtAfter(Long userId, String deviceFingerprint, OffsetDateTime now);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query(
            "update RefreshToken t set t.revokedAt = :now where t.user.id = :userId and t.revokedAt is null")
    int revokeAllForUser(@org.springframework.data.repository.query.Param("userId") Long userId,
                         @org.springframework.data.repository.query.Param("now") OffsetDateTime now);
}
