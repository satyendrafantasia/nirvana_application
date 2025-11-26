package com.nirvana.application.repository.corporate;

import com.nirvana.application.model.corporate.CorporateEmployeeCoupon;
import com.nirvana.application.model.enums.CorporateCouponStatus;
import com.nirvana.application.model.enums.CorporateCouponType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CorporateEmployeeCouponRepository extends JpaRepository<CorporateEmployeeCoupon, Long> {
    List<CorporateEmployeeCoupon> findByUserId(Long userId);
    List<CorporateEmployeeCoupon> findByUserIdAndStatus(Long userId, CorporateCouponStatus status);
    List<CorporateEmployeeCoupon> findByUserIdAndStatusAndCouponType(Long userId, CorporateCouponStatus status, CorporateCouponType couponType);
    List<CorporateEmployeeCoupon> findByUserIdAndStatusAndExpiryDateAfter(Long userId, CorporateCouponStatus status, LocalDate date);
}
