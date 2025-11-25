package com.nirvana.application.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "inventory_calendar",
        uniqueConstraints = @UniqueConstraint(name = "uk_inventory_calendar_spa_service_date", columnNames = {"spa_id", "service_id", "service_date"}),
        indexes = {
                @Index(name = "idx_inventory_calendar_spa_date", columnList = "spa_id,service_date"),
                @Index(name = "idx_inventory_calendar_service_date", columnList = "service_id,service_date")
        }
)
public class InventoryCalendarEntry extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spa_id", nullable = false)
    private Spa spa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private Service service;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "available_units", nullable = false)
    private Integer availableUnits = 0;

    @Column(name = "base_price_cents", nullable = false)
    private Integer basePriceCents = 0;

    @Column(name = "override_price_cents")
    private Integer overridePriceCents;

    @Column(name = "currency", length = 10, nullable = false)
    private String currency = "INR";

    @Column(name = "locked", nullable = false)
    private Boolean locked = Boolean.FALSE;

    @Column(name = "note", length = 1000)
    private String note;
}
