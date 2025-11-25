package com.nirvana.application.model;

import com.nirvana.application.model.enums.SupportOverrideStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "support_override", indexes = {
        @Index(name = "idx_support_override_spa", columnList = "spa_id,created_at"),
        @Index(name = "idx_support_override_booking", columnList = "booking_id")
})
public class SupportOverride extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spa_id")
    private Spa spa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(name = "override_type", length = 100, nullable = false)
    private String overrideType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private SupportOverrideStatus status = SupportOverrideStatus.REQUESTED;

    @Column(name = "payload", length = 4000)
    private String payload;

    @Column(name = "requested_by", length = 100)
    private String requestedBy;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;
}
