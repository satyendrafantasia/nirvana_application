package com.nirvana.application.model.corporate;

import com.nirvana.application.model.BaseEntity;
import com.nirvana.application.model.User;
import com.nirvana.application.model.enums.CorporateCouponStatus;
import com.nirvana.application.model.enums.CorporateCouponType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "corporate_employee_coupon", indexes = {
        @Index(name = "idx_employee_coupon_user", columnList = "user_id,status,expiry_date")
})
public class CorporateEmployeeCoupon extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corporate_id", nullable = false)
    private Corporate corporate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corporate_deal_id", nullable = false)
    private CorporateDeal corporateDeal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corporate_employee_id", nullable = false)
    private CorporateEmployee corporateEmployee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_type", nullable = false, length = 64)
    private CorporateCouponType couponType;

    @Column(name = "total_sessions")
    private Integer totalSessions;

    @Column(name = "remaining_sessions")
    private Integer remainingSessions;

    @Column(name = "global_package_type", length = 128)
    private String globalPackageType;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CorporateCouponStatus status = CorporateCouponStatus.ACTIVE;
}
