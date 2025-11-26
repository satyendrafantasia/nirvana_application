package com.nirvana.application.model.corporate;

import com.nirvana.application.model.BaseEntity;
import com.nirvana.application.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "corporate_coupon_usage_log", indexes = {
        @Index(name = "idx_coupon_usage_coupon", columnList = "corporate_employee_coupon_id,usage_datetime")
})
public class CorporateCouponUsageLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corporate_employee_coupon_id", nullable = false)
    private CorporateEmployeeCoupon corporateEmployeeCoupon;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corporate_id", nullable = false)
    private Corporate corporate;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "spa_id")
    private Long spaId;

    @Column(name = "usage_datetime", nullable = false)
    private OffsetDateTime usageDateTime;

    @Column(name = "session_number")
    private Integer sessionNumber;

    @Column(name = "notes", length = 1000)
    private String notes;
}
