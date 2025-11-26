package com.nirvana.application.model.corporate;

import com.nirvana.application.model.BaseEntity;
import com.nirvana.application.model.enums.CorporateCouponType;
import com.nirvana.application.model.enums.CorporateDealStatus;
import com.nirvana.application.model.enums.CorporatePaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "corporate_deal", indexes = {
        @Index(name = "idx_corporate_deal_corp", columnList = "corporate_id,status")
})
public class CorporateDeal extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corporate_id", nullable = false)
    private Corporate corporate;

    @Column(name = "deal_name", nullable = false, length = 255)
    private String dealName;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_type", nullable = false, length = 64)
    private CorporateCouponType couponType;

    @Column(name = "total_sessions_per_employee")
    private Integer totalSessionsPerEmployee;

    @Column(name = "global_package_type", length = 128)
    private String globalPackageType;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CorporateDealStatus status = CorporateDealStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "corporate_payment_status", nullable = false, length = 32)
    private CorporatePaymentStatus corporatePaymentStatus = CorporatePaymentStatus.PENDING;
}
