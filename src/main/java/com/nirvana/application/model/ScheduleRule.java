package com.nirvana.application.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Weekly schedule rule for a spa (e.g. Mon-Fri 09:00-18:00).
 * Supports marking the weekday as a holiday, and limiting the rule to a date range.
 */
@Entity
@Table(name = "schedule_rule",
        uniqueConstraints = @UniqueConstraint(name = "uk_schedule_spa_day", columnNames = {"spa_id","weekday"}),
        indexes = {
                @Index(name = "idx_schedule_spa_applies", columnList = "spa_id,applies_from,applies_to,weekday")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScheduleRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spa_id", nullable = false)
    private Spa spa;

    /**
     * 0 = Monday, 6 = Sunday (keep consistent with your system)
     */
    @Column(nullable = false)
    private Short weekday;

    @Column(name = "open_local", nullable = false)
    private LocalTime openLocal;

    @Column(name = "close_local", nullable = false)
    private LocalTime closeLocal;

    /**
     * If true this weekday is considered a holiday (closed).
     * openLocal/closeLocal should be ignored when isHoliday == true.
     */
    @Column(name = "is_holiday", nullable = false)
    private Boolean isHoliday = false;

    /**
     * Optional date-range where this rule applies (seasonal schedules).
     * Use LocalDate to represent the date-only range.
     */
    @Column(name = "applies_from")
    private LocalDate appliesFrom;

    @Column(name = "applies_to")
    private LocalDate appliesTo;

    @Column(name = "note", length = 1000)
    private String note;

    /**
     * Optional metadata for future fields; keep as json text.
     */
    @Column(name = "meta", columnDefinition = "json")
    private String metaJson;

    @Version
    private Long version;

    // Auditing fields handled by BaseEntity if you extend it
    @PrePersist
    @PreUpdate
    private void validate() {
        if (appliesFrom != null && appliesTo != null && appliesFrom.isAfter(appliesTo)) {
            throw new IllegalStateException("appliesFrom must be <= appliesTo");
        }
        if (isHoliday == null) {
            isHoliday = false;
        }
        Objects.requireNonNull(weekday, "weekday must not be null");
        Objects.requireNonNull(openLocal, "openLocal must not be null");
        Objects.requireNonNull(closeLocal, "closeLocal must not be null");
    }
}
