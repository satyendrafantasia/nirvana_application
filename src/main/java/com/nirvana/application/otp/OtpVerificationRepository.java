package com.nirvana.application.otp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {

    Optional<OtpVerification> findByVerificationId(String verificationId);

    Optional<OtpVerification> findByRegistrationToken(String registrationToken);

    Optional<OtpVerification> findByLoginToken(String loginToken);

    Optional<OtpVerification> findByPasswordResetToken(String passwordResetToken);

    long countByPhoneNumberAndCreatedAtAfter(String phoneNumber, OffsetDateTime after);

    long countByEmailAndCreatedAtAfter(String email, OffsetDateTime after);
}
