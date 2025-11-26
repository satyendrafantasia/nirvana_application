package com.nirvana.application.model.spa;

import com.nirvana.application.model.BaseEntity;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.enums.spa.SpaPackageLevel;
import com.nirvana.application.model.enums.spa.SpaPackageStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "spa_packages",
        uniqueConstraints = @UniqueConstraint(name = "uk_spa_package_level", columnNames = {"spa_id", "level"}),
        indexes = {
                @Index(name = "idx_spa_package_spa", columnList = "spa_id"),
                @Index(name = "idx_spa_package_status", columnList = "status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpaPackage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spa_id", nullable = false)
    private Spa spa;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 32)
    private SpaPackageLevel level;

    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(name = "free_sessions_count", nullable = false)
    private Integer freeSessionsCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SpaPackageStatus status = SpaPackageStatus.ACTIVE;

    @Version
    private Long version;
}
