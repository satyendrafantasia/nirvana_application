package com.nirvana.application.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "membership_plan", indexes = {
        @Index(name = "idx_membership_spa", columnList = "spa_id,is_active")
})
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MembershipPlan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // null = global plan, otherwise spa-specific
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spa_id")
    @ToString.Exclude
    private Spa spa;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "price_cents", nullable = false)
    private Integer priceCents;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Column(name = "benefits", columnDefinition = "json")
    private String benefitsJson;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "meta", columnDefinition = "json")
    private String metaJson;

    @Version
    private Long version;

    // getters/setters
}
