package com.nirvana.application.model;

import com.nirvana.application.model.enums.KycStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "spa", indexes = {
        @Index(name = "idx_spa_city_state", columnList = "city,state"),
        @Index(name = "idx_spa_active", columnList = "is_active"),
        @Index(name = "idx_spa_name", columnList = "name")
})
public class Spa extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "address_line1")
    private String addressLine1;
    @Column(name = "address_line2")
    private String addressLine2;
    private String city;
    private String state;
    @Column(name = "postal_code")
    private String postalCode;
    private String country;

    private Double lat;
    private Double lon;

    @Column(nullable = false)
    private String timezone; // e.g., Asia/Kolkata

    private String phone;
    private String email;
    private String websiteUrl;

    @Column(nullable = false)
    private Integer rooms = 1; // capacity units
    @Column(name = "max_concurrent_services")
    private Integer maxConcurrentServices = 1;

    // business identifiers
    private String gstin;
    @Column(name = "business_reg_number")
    private String businessRegistrationNumber;
    @Column(name = "owner_name")
    private String ownerName;
    @Column(name = "established_at")
    private OffsetDateTime establishedAt;

    // state flags
    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false)
    private KycStatus kycStatus = KycStatus.PENDING;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified = false; // marketplace verification

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
    private Integer commissionPct = 10; // platform commission

    // contact / social
    @Column(name = "facebook_url")
    private String facebookUrl;
    @Column(name = "instagram_url")
    private String instagramUrl;

    @Column(name = "meta", columnDefinition = "jsonb")
    private String metaJson;

    @Version
    private Long version;
}
