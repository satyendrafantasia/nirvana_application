package com.nirvana.application.repository.corporate;

import com.nirvana.application.model.corporate.CorporateCouponUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CorporateCouponUsageLogRepository extends JpaRepository<CorporateCouponUsageLog, Long> {
    List<CorporateCouponUsageLog> findByCorporateEmployeeCouponId(Long couponId);
    List<CorporateCouponUsageLog> findByUserId(Long userId);
}
