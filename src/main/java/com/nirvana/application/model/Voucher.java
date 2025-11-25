package com.nirvana.application.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "voucher", indexes = {
        @Index(name = "idx_voucher_code", columnList = "code", unique = true),
        @Index(name = "idx_voucher_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Voucher extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(name = "amount_cents", nullable = false)
    private Integer amountCents;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "issued_by", length = 128)
    private String issuedBy;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "redeemed_at")
    private OffsetDateTime redeemedAt;

    @Column(name = "meta", columnDefinition = "json")
    private String metaJson;
}
