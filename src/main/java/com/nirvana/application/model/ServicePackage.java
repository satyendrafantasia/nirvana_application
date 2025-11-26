package com.nirvana.application.model;

import com.nirvana.application.model.enums.PackageType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "packages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServicePackage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "package_type", nullable = false, unique = true, length = 32)
    private PackageType packageType;

    @Column(name = "price_cents", nullable = false)
    private Integer priceCents;

    @Column(name = "session_count", nullable = false)
    private Integer sessionCount;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "validity_days")
    private Integer validityDays;

    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "spa_packages",
            joinColumns = @JoinColumn(name = "package_id"),
            inverseJoinColumns = @JoinColumn(name = "spa_id")
    )
    @Builder.Default
    private Set<Spa> eligibleSpas = new HashSet<>();
}
