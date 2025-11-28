package com.nirvana.application.otp;

import com.nirvana.application.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "otp_verification", indexes = {
        @Index(name = "idx_otp_verification_id", columnList = "verificationId", unique = true),
        @Index(name = "idx_otp_verification_token", columnList = "registrationToken")
})
public class OtpVerification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String verificationId;

    @Column(length = 32)
    private String phoneNumber;

    @Column(length = 320)
    private String email;

    @Column(nullable = false, length = 6)
    private String otpCode;

    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    @Column(nullable = false)
    private Integer attempts;

    @Column(nullable = false)
    private Integer maxAttempts;

    @Column(nullable = false)
    private Boolean verified;

    @Column(length = 64)
    private String registrationToken;

    private OffsetDateTime registrationTokenExpiresAt;

    @Column(nullable = false)
    private Boolean registrationConsumed;
}
