package com.nirvana.application.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "blackout_window", indexes = {
        @Index(name = "idx_blackout_spa", columnList = "spa_id,start_ts,end_ts"),
        @Index(name = "idx_blackout_provider", columnList = "provider_id,start_ts,end_ts")
})
public class BlackoutWindow extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spa_id", nullable = false)
    private Spa spa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private ProviderUser provider;

    @Column(name = "start_ts", nullable = false)
    private OffsetDateTime startTs;

    @Column(name = "end_ts", nullable = false)
    private OffsetDateTime endTs;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "created_by", length = 100)
    private String createdBy;
}
