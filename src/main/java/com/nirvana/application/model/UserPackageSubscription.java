package com.nirvana.application.model;

import com.nirvana.application.model.enums.PackageStatus;
import com.nirvana.application.model.enums.PackageType;
import com.nirvana.application.model.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_package_subscription",
        indexes = {
                @Index(name = "idx_subscription_user_status", columnList = "user_id,status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPackageSubscription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id", nullable = false)
    private ServicePackage servicePackage;

    @Enumerated(EnumType.STRING)
    @Column(name = "package_type", nullable = false, length = 32)
    private PackageType packageType;

    @Column(name = "purchase_date", nullable = false)
    private OffsetDateTime purchaseDate;

    @Column(name = "expiry_date")
    private OffsetDateTime expiryDate;

    @Column(name = "remaining_sessions", nullable = false)
    private Integer remainingSessions;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PackageStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 32)
    private PaymentStatus paymentStatus;

    @Column(name = "payment_reference", length = 128)
    private String paymentReference;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    @OneToMany(mappedBy = "subscription", fetch = FetchType.LAZY)
    @Builder.Default
    private List<UserPackageUsageLog> usageLogs = new ArrayList<>();

    @Version
    private Long version;
}
