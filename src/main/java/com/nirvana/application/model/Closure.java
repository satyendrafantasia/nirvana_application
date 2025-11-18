package com.nirvana.application.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

/**
 * Represents an ad-hoc closure window (maintenance, public holiday, owner absence, etc).
 * Use rrule to express recurrence (RFC5545) where needed.
 */
@Entity
@Table(name = "closure",
        indexes = {
                @Index(name = "idx_closure_window", columnList = "spa_id,start_ts,end_ts"),
                @Index(name = "idx_closure_type", columnList = "spa_id,type,start_ts")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Closure {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spa_id", nullable = false)
    private Spa spa;

    /**
     * Exact closure window (timezone-aware).
     */
    @Column(name = "start_ts", nullable = false)
    private OffsetDateTime startTs;

    @Column(name = "end_ts", nullable = false)
    private OffsetDateTime endTs;

    /**
     * Human-friendly type code. Prefer enum in future ("MAINTENANCE","PUBLIC_HOLIDAY","OWNER_ABSENCE", etc).
     */
    @Column(name = "type", length = 64)
    private String type;

    /**
     * If true, closure repeats according to rrule (or other recurrence config).
     * If true and rrule is null, treat as "repeat forever" by business rule or consider error.
     */
    @Column(name = "is_recurring", nullable = false)
    private Boolean isRecurring = false;

    /**
     * Optional recurrence rule (RFC5545 RRULE) to express recurring closures:
     * e.g., "FREQ=YEARLY;BYMONTH=1;BYMONTHDAY=26" for Republic Day annually.
     */
    @Column(name = "rrule", length = 1000)
    private String rrule;

    /**
     * Scope can be used to narrow a closure to specific services or rooms.
     * Example values: "ALL", "SERVICE:123", "ROOM:2". Keep parsing rules documented.
     */
    @Column(name = "scope", length = 200)
    private String scope;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "meta", columnDefinition = "jsonb")
    private String metaJson;

    @Version
    private Long version;

    @PrePersist
    @PreUpdate
    private void validate() {
        if (startTs == null) throw new IllegalStateException("startTs is required");
        if (endTs == null) throw new IllegalStateException("endTs is required");
        if (startTs.isAfter(endTs)) throw new IllegalStateException("startTs must be <= endTs");
        if (isRecurring == null) isRecurring = false;
        if (isRecurring && (rrule == null || rrule.isBlank())) {
            // Allow but warn via logs in prod; throwing would force always having rrule
            // throw new IllegalStateException("recurring closures should specify an rrule");
        }
    }
}
