package com.nirvana.application.model;

import com.nirvana.application.model.enums.BookingSource;
import com.nirvana.application.model.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "booking", indexes = {
        @Index(name = "idx_booking_user", columnList = "user_id,created_at DESC"),
        @Index(name = "idx_booking_spa", columnList = "spa_id,created_at DESC"),
        @Index(name = "idx_booking_ref", columnList = "booking_reference", unique = true)
})
public class Booking extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_reference", unique = true, nullable = false)
    private String bookingReference; // UUID / human friendly

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spa_id", nullable = false)
    private Spa spa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false, unique = true)
    private Slot slot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingSource source = BookingSource.APP;

    @Column(name = "price_cents", nullable = false)
    private Integer priceCents;

    @Column(nullable = false)
    private String currency = "INR";

    @Column(name = "tax_cents", nullable = false)
    private Integer taxCents = 0;

    @Column(name = "deposit_cents", nullable = false)
    private Integer depositCents;

    @Column(name = "remainder_cents", nullable = false)
    private Integer remainderCents;

    @Column(name = "discount_cents", nullable = false)
    private Integer discountCents = 0;

    @Column(name = "coupon_code")
    private String couponCode;

    @Column(name = "policy_snapshot", columnDefinition = "jsonb", nullable = false)
    private String policySnapshotJson;

    @Column(name = "customer_notes")
    private String customerNotes;

    @Column(name = "provider_notes")
    private String providerNotes;

    @Column(name = "arrived_at")
    private OffsetDateTime arrivedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "no_show_marked_at")
    private OffsetDateTime noShowMarkedAt;

    // expanded booking fields
    @Column(name = "guest_count", nullable = false)
    private Integer guestCount = 1;
    @Column(name = "external_booking_id")
    private String externalBookingId; // OTAs, phone reservations

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_assigned_id")
    private ProviderUser providerAssigned;

    @Column(name = "items", columnDefinition = "jsonb")
    private String itemsJson; // add-ons, products sold with booking

    @Column(name = "tip_cents", nullable = false)
    private Integer tipCents = 0;

    @Column(name = "tax_breakdown", columnDefinition = "jsonb")
    private String taxBreakdownJson;

    @Column(name = "invoice_url")
    private String invoiceUrl;

    @Column(name = "refund_status")
    private String refundStatus; // "none","requested","completed"

    @Column(name = "cancellation_reason_code")
    private String cancellationReasonCode;

    @Column(name = "rating_given", nullable = false)
    private Boolean ratingGiven = false;

    @Column(name = "scheduled_by")
    private String scheduledBy; // "user","spa","admin"

    @Column(name = "channel")
    private String channel; // "mobile","web","kiosk","phone"

    @Column(name = "meta", columnDefinition = "jsonb")
    private String metaJson;

    @Version
    private Long version;
}
