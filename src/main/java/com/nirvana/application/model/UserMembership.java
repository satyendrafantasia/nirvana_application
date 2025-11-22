package com.nirvana.application.model;

import com.nirvana.application.model.enums.MembershipStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_membership", indexes = {
        @Index(name = "idx_user_membership_user", columnList = "user_id,status"),
        @Index(name = "idx_user_membership_spa", columnList = "spa_id,status")
})
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class UserMembership extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    // denormalize spa & plan for quick filtering
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spa_id")
    @ToString.Exclude
    private Spa spa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    @ToString.Exclude
    private MembershipPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MembershipStatus status = MembershipStatus.ACTIVE;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    @Column(name = "meta", columnDefinition = "json")
    private String metaJson;

    @Version
    private Long version;

    // getters/setters
}
