package com.nirvana.application.model;

import com.nirvana.application.model.enums.RefundRequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "payment_refund", indexes = {
        @Index(name = "idx_payment_refund_booking", columnList = "booking_id"),
        @Index(name = "idx_payment_refund_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRefund extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private User processedBy;

    @Column(name = "amount_cents", nullable = false)
    private Integer amountCents;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Column(name = "reason", length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RefundRequestStatus status;

    @Column(name = "prefer_voucher", nullable = false)
    private Boolean preferVoucher = false;

    @Column(name = "gateway_refund_id", length = 128)
    private String gatewayRefundId;

    @Column(name = "voucher_code", length = 64)
    private String voucherCode;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "meta", columnDefinition = "json")
    private String metaJson;
}
