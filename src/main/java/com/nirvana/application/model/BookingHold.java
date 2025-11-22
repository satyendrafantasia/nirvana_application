package com.nirvana.application.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.time.OffsetDateTime;
import java.util.Objects;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "booking_hold", indexes = {
        @Index(name = "idx_hold_user", columnList = "user_id,expires_at"),
        @Index(name = "idx_hold_slot", columnList = "slot_id,expires_at")
})
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class BookingHold extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // user who is holding the slot (nullable for guest flows if needed)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spa_id", nullable = false)
    @ToString.Exclude
    private Spa spa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false)
    @ToString.Exclude
    private Slot slot;

    // services in this hold (could be multiple add-ons etc.)
    @Column(name = "services_json", columnDefinition = "json")
    private String servicesJson;

    @Column(name = "hold_token", unique = true, nullable = false, length = 128)
    private String holdToken;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "converted_to_booking", nullable = false)
    private boolean convertedToBooking = false;

    @Column(name = "meta", columnDefinition = "json")
    private String metaJson;

    @Version
    private Long version;

    // getters/setters

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        BookingHold that = (BookingHold) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
