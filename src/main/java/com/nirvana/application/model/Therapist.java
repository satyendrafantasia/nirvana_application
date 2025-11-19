package com.nirvana.application.model;

import com.nirvana.application.model.enums.GenderAllowed;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Therapist (provider) representing a person who delivers services at a Spa.
 *
 * - imagesJson : jsonb array of image URLs and meta (preferred for flexible metadata).
 * - services : Many-to-many mapping to Service entity (the services this therapist can deliver).
 * - reviewsJson : lightweight JSON blob (list of review ids or small summaries). Replace with relation if you need referential integrity.
 */
@Entity
@Table(name = "therapist",
        indexes = {
                @Index(name = "idx_therapist_spa", columnList = "spa_id"),
                @Index(name = "idx_therapist_name", columnList = "name")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Therapist extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Link therapist to a spa (if therapists are exclusive to a spa)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spa_id")
    private Spa spa;

    // Basic identity
    @Column(nullable = false)
    private String name;

    @Column(name = "display_name")
    private String displayName;

    /**
     * Type/category of therapist: "massage", "physio", "esthetician", etc.
     */
    @Column(name = "type", length = 128)
    private String type;

    // contact & profile
    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "profile_image_url", length = 1000)
    private String profileImageUrl;

    // Multiple images stored as jsonb: [{"url":"...","alt":"...","order":1}, ...]
    @Column(name = "images", columnDefinition = "jsonb")
    private String imagesJson;

    // Services offered (join table)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "therapist_service",
            joinColumns = @JoinColumn(name = "therapist_id"),
            inverseJoinColumns = @JoinColumn(name = "service_id"),
            indexes = {
                    @Index(name = "idx_therapist_service_th", columnList = "therapist_id"),
                    @Index(name = "idx_therapist_service_sv", columnList = "service_id")
            })
    @Builder.Default
    private Set<Service> services = new HashSet<>();

    // Freeform biography / experience summary
    @Column(name = "bio", length = 2000)
    private String bio;

    // Demographic / profile fields
    @Column(name = "ethnicity", length = 128)
    private String ethnicity;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 16)
    private GenderAllowed gender;

    @Column(name = "country_of_origin", length = 128)
    private String countryOfOrigin;

    // Languages as text[] in DB (Postgres) for quick filtering
    @Column(name = "languages", columnDefinition = "text[]")
    private String[] languages;

    // Certifications and qualifications
    @Column(name = "certifications", columnDefinition = "jsonb")
    private String certificationsJson;

    // Rating aggregates (materialized for fast reads)
    @Column(name = "rating_avg")
    private Float ratingAvg = 0f;

    @Column(name = "rating_count")
    private Integer ratingCount = 0;

    // Optionally retain a small reviews summary or list of review ids; consider separate table for full reviews
    @Column(name = "reviews", columnDefinition = "jsonb")
    private String reviewsJson;

    // Pricing & experience
    @Column(name = "hourly_rate_cents")
    private Integer hourlyRateCents;

    @Column(name = "currency", length = 8)
    private String currency = "INR";

    @Column(name = "experience_years")
    private Integer experienceYears;

    // operational flags
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = true; // quick flag for real-time availability

    // flexible metadata
    @Column(name = "meta", columnDefinition = "jsonb")
    private String metaJson;

    // optimistic locking
    @Version
    private Long version;

    // Optional audit: last seen or last updated (BaseEntity has createdAt/updatedAt)
    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;
}
