package com.nirvana.application.model;

import com.nirvana.application.model.enums.GenderAllowed;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;

@Entity
@Table(name = "service", indexes = @Index(name = "idx_service_spa", columnList = "spa_id"))
@Data
@Builder
public class Service extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spa_id", nullable = false)
    private Spa spa;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "duration_min", nullable = false)
    private Integer durationMin;

    @Column(name = "buffer_min", nullable = false)
    private Integer bufferMin = 0;

    @Column(name = "base_price_cents", nullable = false)
    private Integer basePriceCents;

    @Column(nullable = false)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "gender_allowed", nullable = false)
    private GenderAllowed genderAllowed = GenderAllowed.ANY;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // expanded
    @Column(name = "category")
    private String category; // e.g., "massage","facial"
    @Column(name = "sub_category")
    private String subCategory;
    @Column(name = "service_code", unique = true)
    private String serviceCode; // SKU-like

    @Column(name = "min_persons", nullable = false)
    private Integer minPersons = 1;
    @Column(name = "max_persons", nullable = false)
    private Integer maxPersons = 1;

    @Column(name = "images", columnDefinition = "jsonb")
    private String imagesJson;
    @Column(name = "equipment_required", columnDefinition = "text[]")
    private String[] equipmentRequired;
    @Column(name = "therapist_gender_preference")
    private String therapistGenderPreference;

    @Column(name = "cancellation_policy_json", columnDefinition = "jsonb")
    private String cancellationPolicyJson;

    @Column(name = "price_breakdown", columnDefinition = "jsonb")
    private String priceBreakdownJson; // taxes, service fee etc.

    @Column(name = "is_visible_on_marketplace", nullable = false)
    private Boolean isVisibleOnMarketplace = true;

    @Column(name = "meta", columnDefinition = "jsonb")
    private String metaJson;

    @Version
    private Long version;
}

