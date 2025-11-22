package com.nirvana.application.model;

import com.nirvana.application.model.enums.GenderAllowed;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "service", indexes = @Index(name = "idx_service_spa", columnList = "spa_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    private String category;
    // e.g., "massage","facial"
    @Column(name = "sub_category")
    private String subCategory;

    @Column(name = "service_code", unique = true)
    private String serviceCode;
    // SKU-like
    @Column(name = "price_cents")
    private Integer priceCents;

    @Column(name = "duration_minutes" )
    private Integer durationMinutes;

    @OneToMany(mappedBy = "service", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Therapist> therapists = new HashSet<>();

    private Long version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Column(name = "min_persons", nullable = false)
    private Integer minPersons = 1;
    @Column(name = "max_persons", nullable = false)
    private Integer maxPersons = 1;

    @Column(name = "images", columnDefinition = "json")
    private String imagesJson;
    @Column(name = "equipment_required", columnDefinition = "json")
    private String[] equipmentRequired;
    @Column(name = "therapist_gender_preference")
    private String therapistGenderPreference;

    @Column(name = "cancellation_policy_json", columnDefinition = "json")
    private String cancellationPolicyJson;

    @Column(name = "price_breakdown", columnDefinition = "json")
    private String priceBreakdownJson; // taxes, service fee etc.

    @Column(name = "is_visible_on_marketplace", nullable = false)
    private Boolean isVisibleOnMarketplace = true;

    @Column(name = "meta", columnDefinition = "json")
    private String metaJson;


}

