package com.nirvana.application.model;

import com.nirvana.application.model.enums.Currency;
import com.nirvana.application.model.enums.PaymentMethod;
import com.nirvana.application.model.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "payment", indexes = {
        @Index(name = "uk_payment_intent", columnList = "intent_id", unique = true),
        @Index(name = "idx_payment_booking", columnList = "booking_id")
})
public class Payment extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @Column(nullable = false)
    private String gateway; // 'razorpay' | 'stripe' | 'payu'

    @Column(name = "intent_id", unique = true)
    private String intentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.INIT;

    @Column(unique = true, nullable = false)
    private String transactionId;

    @Column(nullable = false)
    private BigDecimal totalPrice;

    @Column(name = "amount_cents", nullable = false)
    private Integer amountCents;

    @Column(nullable = false)
    private String currency = "INR";

    @Column(name = "fee_cents", nullable = false)
    private Integer feeCents = 0;

    @Column(name = "refunded_cents", nullable = false)
    private Integer refundedCents = 0;

    @Column(name = "captured_at")
    private OffsetDateTime capturedAt;

    @Column(name = "refunded_at")
    private OffsetDateTime refundedAt;

    @Column(name = "meta", columnDefinition = "jsonb")
    private String metaJson;

    // expanded
    @Column(name = "payment_method")
    private String paymentMethod; // "card","upi","netbanking","wallet"
    @Column(name = "card_brand")
    private String cardBrand;
    @Column(name = "card_last4")
    private String cardLast4;
    @Column(name = "bank_txn_id")
    private String bankTxnId;
    @Column(name = "settlement_status")
    private String settlementStatus; // "pending","settled"
    @Column(name = "settlement_date")
    private OffsetDateTime settlementDate;
    @Column(name = "currency_conversion_rate")
    private Double currencyConversionRate;

    @Column(name = "payout_id")
    private String payoutId; // platform payout ref
    @Column(name = "payout_status")
    private String payoutStatus;

    @Version
    private Long version;
}
