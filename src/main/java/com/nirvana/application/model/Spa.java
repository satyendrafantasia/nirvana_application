package com.nirvana.application.model;

import com.nirvana.application.model.enums.KycStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
@Entity
@Table(name = "spa", indexes = {
        @Index(name = "idx_spa_city_state", columnList = "city,state"), // you'll update to address fields in queries
        @Index(name = "idx_spa_active", columnList = "is_active"),
        @Index(name = "idx_spa_name", columnList = "name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Spa extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    // Address embedded, no OneToOne
    @Embedded
    private Address address;

    @Column(nullable = false ,  insertable=false, updatable=false)
    private String timezone; // e.g., Asia/Kolkata

    private String phone;
    private String email;
    private String websiteUrl;



    @Column(name = "max_concurrent_services")
    private Integer maxConcurrentServices = 1;

    @OneToMany(mappedBy = "spa", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Builder.Default
    private List<Therapist> therapists = new ArrayList<>();

    // business identifiers
    private String gstin;

    @Column(name = "business_reg_number")
    private String businessRegistrationNumber;

    @Column(name = "owner_name")
    private String ownerName;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "spa_manager_id")
    private SpaManager spaManager;

    @Column(name = "established_at")
    private OffsetDateTime establishedAt;

    // KYC
    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false)
    private KycStatus kycStatus = KycStatus.PENDING;

    @Column(name = "kyc_requested_at")
    private OffsetDateTime kycRequestedAt;

    @Column(name = "kyc_approved_at")
    private OffsetDateTime kycApprovedAt;

    @Column(name = "kyc_rejected_at")
    private OffsetDateTime kycRejectedAt;

    @Column(name = "kyc_rejected_reason", length = 1000)
    private String kycRejectedReason;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified = false;

    @Column(name = "is_featured", nullable = false)
    private Boolean isFeatured = false;

    // ratings & metrics
    @Column(name = "rating_avg")
    private Float ratingAvg = 0f;

    @Column(name = "rating_count")
    private Integer ratingCount = 0;

    @Column(name = "total_bookings")
    private Long totalBookings = 0L;

    // images, amenities, tags
    @Column(name = "images", columnDefinition = "jsonb")
    private String imagesJson;

    @Column(name = "amenities", columnDefinition = "text[]")
    private String[] amenities;

    @Column(name = "tags", columnDefinition = "text[]")
    private String[] tags;

    // policies & financials
    @Column(name = "default_currency", nullable = false)
    private String defaultCurrency = "INR";

    @Column(name = "tax_percent", nullable = false)
    private Integer taxPercent = 18;

    @Column(name = "commission_pct", nullable = false)
    private Integer commissionPct = 10;

    // booking config
    @Column(name = "max_advance_booking_days")
    private Integer maxAdvanceBookingDays;

    @Column(name = "min_notice_minutes")
    private Integer minNoticeMinutes;

    // contact / social
    @Column(name = "facebook_url")
    private String facebookUrl;

    @Column(name = "instagram_url")
    private String instagramUrl;

    // optional: google place id if not in Address
    @Column(name = "google_place_id", insertable = false, updatable = false)
    private String googlePlaceId;

    @Column(name = "meta", columnDefinition = "jsonb" , insertable=false, updatable=false)
    private String metaJson;

    @Version
    private Long version;
}
