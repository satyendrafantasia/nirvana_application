package com.nirvana.application.model;

import com.nirvana.application.model.enums.SlotStatus;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "slot", indexes = {
        // existing indexes
})
public class Slot extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spa_id", nullable = false)
    private Spa spa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @Column(name = "start_ts", nullable = false)
    private OffsetDateTime startTs;

    @Column(name = "end_ts", nullable = false)
    private OffsetDateTime endTs;

    @Column(name = "capacity_unit", nullable = false)
    private Short capacityUnit = 1; // 1..rooms

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlotStatus status = SlotStatus.OPEN;

    @Column(name = "hold_expires_ts")
    private OffsetDateTime holdExpiresTs;

    // expanded
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_provider_user_id")
    private ProviderUser assignedProviderUser;

    @Column(name = "room_number")
    private String roomNumber;

    @Column(name = "booked_units", nullable = false)
    private Short bookedUnits = 0;

    @Column(name = "hold_token")
    private String holdToken; // id returned by hold call

    @Column(name = "is_blocked", nullable = false)
    private Boolean isBlocked = false; // admin block

    @Column(name = "meta", columnDefinition = "jsonb")
    private String metaJson;

    @Version
    private Long version;
}

