package com.nirvana.application.otp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpVerification> findByVerificationId(String verificationId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpVerification> findByRegistrationToken(String registrationToken);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpVerification> findByLoginToken(String loginToken);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpVerification> findByPasswordResetToken(String passwordResetToken);

    long countByPhoneNumberAndCreatedAtAfter(String phoneNumber, OffsetDateTime after);

    long countByEmailAndCreatedAtAfter(String email, OffsetDateTime after);
}
