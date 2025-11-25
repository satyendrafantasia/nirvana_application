package com.nirvana.application.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "provider_audit_log", indexes = {
        @Index(name = "idx_provider_audit_spa", columnList = "spa_id,created_at"),
        @Index(name = "idx_provider_audit_actor", columnList = "actor_type,actor_id")
})
public class ProviderAuditLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spa_id")
    private Spa spa;

    @Column(name = "actor_type", length = 50)
    private String actorType;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "action", length = 150, nullable = false)
    private String action;

    @Column(name = "details", length = 4000)
    private String details;
}
