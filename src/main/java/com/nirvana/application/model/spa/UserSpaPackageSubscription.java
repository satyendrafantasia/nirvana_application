package com.nirvana.application.model.spa;

import com.nirvana.application.model.BaseEntity;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.User;
import com.nirvana.application.model.enums.spa.PaymentStatus;
import com.nirvana.application.model.enums.spa.SpaPackageLevel;
import com.nirvana.application.model.enums.spa.UserSpaPackageStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_spa_package_subscription",
        indexes = {
                @Index(name = "idx_user_spa_package_status", columnList = "user_id,status,spa_id"),
                @Index(name = "idx_user_spa_package_payment", columnList = "payment_status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSpaPackageSubscription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spa_id", nullable = false)
    private Spa spa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spa_package_id", nullable = false)
    private SpaPackage spaPackage;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 32)
    private SpaPackageLevel level;

    @Column(name = "price_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal pricePaid;

    @Column(name = "total_sessions", nullable = false)
    private Integer totalSessions;

    @Column(name = "remaining_sessions", nullable = false)
    private Integer remainingSessions;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private UserSpaPackageStatus status;

    @Column(name = "purchase_date", nullable = false)
    private OffsetDateTime purchaseDate;

    @Column(name = "expiry_date")
    private OffsetDateTime expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 32)
    private PaymentStatus paymentStatus;

    @Column(name = "payment_reference_id", length = 128)
    private String paymentReferenceId;

    @OneToMany(mappedBy = "subscription", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    private List<UserSpaPackageUsageLog> usageLogs = new ArrayList<>();

    @Version
    private Long version;
}
