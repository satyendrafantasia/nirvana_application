package com.nirvana.application.model;

import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.RoleType;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

/**
 * Tracks every status change or significant event in a booking's lifecycle.
 * Immutable audit-style record — do NOT update after insert.
 */
@Entity
@Table(name = "booking_event",
        indexes = {
                @Index(name = "idx_booking_event_lookup", columnList = "booking_id,created_at"),
                @Index(name = "idx_booking_event_type", columnList = "new_status,actor_type")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---- RELATION ----
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    // ---- STATUS TRANSITION ----
    @Enumerated(EnumType.STRING)
    @Column(name = "prev_status")
    private BookingStatus prevStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private BookingStatus newStatus;

    // ---- EXECUTION CONTEXT ----
    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false)
    private RoleType actorType;   // USER | PROVIDER | SYSTEM

    @Column(name = "actor_id")
    private Long actorId;          // Id of user/provider/system entity

    // ---- PAYLOAD ----
    @Column(name = "event_payload", columnDefinition = "jsonb")
    private String eventPayloadJson;   // Extra context for audit

    // ---- TIMESTAMP ----
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    // Optimistic locking (optional but good practice)
    @Version
    private Long version;

    // ---- VALIDATION HOOK ----
    @PrePersist
    private void validate() {
        if (newStatus == null)
            throw new IllegalStateException("newStatus cannot be null in BookingEvent");
        if (actorType == null)
            throw new IllegalStateException("actorType cannot be null in BookingEvent");
        if (createdAt == null)
            createdAt = OffsetDateTime.now();
    }
}
