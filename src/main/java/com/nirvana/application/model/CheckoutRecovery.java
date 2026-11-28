package com.nirvana.application.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/** One reminder sent for a checkout the guest left unpaid, and what came of it. */
@Entity
@Table(name = "checkout_recovery")
@Getter
@Setter
@NoArgsConstructor
public class CheckoutRecovery {
    public enum Channel { EMAIL, WHATSAPP }
    /** PENDING while it is being sent; FAILED reminders are not retried. */
    public enum Status { PENDING, SENT, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "spa_id", nullable = false)
    private Long spaId;

    @Column(nullable = false, length = 16)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Channel channel;

    @Column(nullable = false, length = 16)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Status status;

    @Column(name = "token_nonce", nullable = false, length = 32)
    private String tokenNonce;

    @Column(name = "token_expires_at", nullable = false)
    private OffsetDateTime tokenExpiresAt;

    @Column(name = "sent_at", nullable = false)
    private OffsetDateTime sentAt;

    @Column(name = "clicked_at")
    private OffsetDateTime clickedAt;

    @Column(name = "click_count", nullable = false)
    private int clickCount;

    @Column(name = "converted_at")
    private OffsetDateTime convertedAt;

    @Column(name = "converted_booking_id")
    private Long convertedBookingId;

    @Column(name = "recovered_cents")
    private Integer recoveredCents;
}
