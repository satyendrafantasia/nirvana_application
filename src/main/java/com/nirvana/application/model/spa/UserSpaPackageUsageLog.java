package com.nirvana.application.model.spa;

import com.nirvana.application.model.BaseEntity;
import com.nirvana.application.model.Booking;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_spa_package_usage_log",
        indexes = {
                @Index(name = "idx_user_spa_usage", columnList = "user_id,spa_id"),
                @Index(name = "idx_spa_subscription_usage", columnList = "user_spa_package_subscription_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSpaPackageUsageLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_spa_package_subscription_id", nullable = false)
    private UserSpaPackageSubscription subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spa_id", nullable = false)
    private Spa spa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(name = "usage_date", nullable = false)
    private OffsetDateTime usageDate;

    @Column(name = "session_number", nullable = false)
    private Integer sessionNumber;

    @Column(name = "notes", length = 512)
    private String notes;
}
