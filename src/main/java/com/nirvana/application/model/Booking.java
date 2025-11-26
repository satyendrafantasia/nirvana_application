package com.nirvana.application.model;
import com.nirvana.application.model.enums.BookingPaymentType;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.BookingChannel;
import com.nirvana.application.model.enums.PaymentMode;
import com.nirvana.application.model.enums.RefundStatus;
import com.nirvana.application.model.enums.CancellationActor;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "booking",
        indexes = {
                @Index(name = "idx_booking_user", columnList = "user_id,created_at DESC"),
                @Index(name = "idx_booking_spa", columnList = "spa_id,created_at DESC"),
                @Index(name = "idx_booking_ref", columnList = "booking_reference", unique = true),
                @Index(name = "idx_booking_spa_status_start", columnList = "spa_id,status,start_ts"),
                @Index(name = "idx_booking_slot", columnList = "slot_id", unique = true)
        }
)
public class Booking extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Public-facing booking identifier (UUID / human-friendly code).
     * Use this in APIs, emails, logs etc. – not the numeric id.
     */
    @Column(name = "booking_reference", unique = true, nullable = false, length = 64)
    private String bookingReference;

    // -------- PARTICIPANTS & CONTEXT --------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // who booked

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spa_id", nullable = false)
    private Spa spa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    @OneToOne(mappedBy = "booking", fetch = FetchType.LAZY)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_membership_id")
    private UserMembership userMembership;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_subscription_id")
    private UserPackageSubscription packageSubscription;



    /**
     * The concrete time slot reserved by this booking.
     * We ALSO snapshot start/end below to survive slot changes.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false, unique = true)
    private Slot slot;

    /**
     * Therapist / provider assigned (optional).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_assigned_id")
    private ProviderUser providerAssigned;

    /**
     * Explicit therapist selection by the guest (if spa allows it).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "therapist_id")
    private Therapist therapist;

    @Column(name = "therapist_type", length = 128)
    private String therapistType;

    // -------- TIME SNAPSHOTS --------

    /**
     * Snapshotted start/end times in spa timezone (or UTC).
     * Copy from Slot at booking time.
     */
    @Column(name = "start_ts", nullable = false)
    private OffsetDateTime startTs;

    @Column(name = "end_ts", nullable = false)
    private OffsetDateTime endTs;

    @Column(name = "arrived_at")
    private OffsetDateTime arrivedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "no_show_marked_at")
    private OffsetDateTime noShowMarkedAt;

    @Column(name = "last_status_changed_at")
    private OffsetDateTime lastStatusChangedAt;

    // -------- STATUS & SOURCE --------

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BookingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", length = 16)
    private PaymentMode paymentMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", length = 32)
    private BookingPaymentType paymentType = BookingPaymentType.STANDARD;


    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 32)
    private BookingChannel channel; // MOBILE, WEB, KIOSK, PHONE, API

    // who scheduled this booking
    @Enumerated(EnumType.STRING)
    @Column(name = "scheduled_by", length = 32)
    private CancellationActor scheduledBy; // reuse actor enum: USER, SPA, ADMIN, SYSTEM

    // who cancelled this booking (if cancelled)
    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by", length = 32)
    private CancellationActor cancelledBy;

    @Column(name = "cancellation_reason_code", length = 100)
    private String cancellationReasonCode;

    @Column(name = "cancellation_reason_text", length = 1000)
    private String cancellationReasonText;

    @Column(name = "reschedule_count", nullable = false)
    private Integer rescheduleCount = 0;

    // -------- FINANCIALS --------

    @Column(name = "price_cents", nullable = false)
    private Integer priceCents;

    @Column(nullable = false, length = 10)
    private String currency = "INR";

    @Column(name = "tax_cents", nullable = false)
    private Integer taxCents = 0;

    @Column(name = "deposit_cents", nullable = false)
    private Integer depositCents;

    @Column(name = "remainder_cents", nullable = false)
    private Integer remainderCents;

    @Column(name = "discount_cents", nullable = false)
    private Integer discountCents = 0;

    @Column(name = "coupon_code", length = 64)
    private String couponCode;

    @Column(name = "tip_cents", nullable = false)
    private Integer tipCents = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", length = 32, nullable = false)
    private RefundStatus refundStatus = RefundStatus.NONE;

    @Column(name = "invoice_url", length = 1000)
    private String invoiceUrl;

    /**
     * Snapshot of policies (cancellation, no-show, late arrival) at booking time.
     */
    @Column(name = "policy_snapshot", columnDefinition = "json", nullable = false)
    private String policySnapshotJson;

    /**
     * Detailed tax breakdown snapshot (CGST/SGST, rates per line item, etc.).
     */
    @Column(name = "tax_breakdown", columnDefinition = "json")
    private String taxBreakdownJson;

    /**
     * Add-ons / products sold with this booking (snapshot).
     */
    @Column(name = "items", columnDefinition = "json")
    private String itemsJson;

    // -------- RELATION TO PAYMENT --------

    @OneToOne(mappedBy = "booking", fetch = FetchType.LAZY)
    private Payment payment;

    // -------- MISC BOOKING METADATA --------

    @Column(name = "guest_count", nullable = false)
    private Integer guestCount = 1;

    @Column(name = "external_booking_id", length = 128)
    private String externalBookingId; // OTAs, phone reservation system, etc.

    @Column(name = "customer_notes", length = 2000)
    private String customerNotes;

    @Column(name = "provider_notes", length = 2000)
    private String providerNotes;

    @Column(name = "rating_given", nullable = false)
    private Boolean ratingGiven = false;

    @Column(name = "is_test_booking", nullable = false)
    private Boolean isTestBooking = false;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "meta", columnDefinition = "json")
    private String metaJson;

    @Version
    private Long version;
}
