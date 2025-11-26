package com.nirvana.application.service.impl.corporate;

import com.nirvana.application.exception.CorporateCouponNotFoundException;
import com.nirvana.application.model.corporate.CorporateEmployeeCoupon;
import com.nirvana.application.model.corporate.CorporateCouponUsageLog;
import com.nirvana.application.model.dto.corporate.CorporateCouponResponse;
import com.nirvana.application.model.dto.corporate.CorporateCouponUsageResponse;
import com.nirvana.application.model.enums.CorporateCouponStatus;
import com.nirvana.application.repository.corporate.CorporateCouponUsageLogRepository;
import com.nirvana.application.repository.corporate.CorporateEmployeeCouponRepository;
import com.nirvana.application.service.corporate.CorporateCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CorporateCouponServiceImpl implements CorporateCouponService {

    private final CorporateEmployeeCouponRepository couponRepository;
    private final CorporateCouponUsageLogRepository usageLogRepository;

    @Override
    public List<CorporateCouponResponse> getUserCorporateCoupons(Long userId) {
        return couponRepository.findByUserId(userId).stream().map(this::toResponse).toList();
    }

    @Override
    public CorporateCouponResponse getUserCorporateCouponDetails(Long userId, Long couponId) {
        CorporateEmployeeCoupon coupon = couponRepository.findById(couponId)
                .filter(c -> c.getUser().getId().equals(userId))
                .orElseThrow(() -> new CorporateCouponNotFoundException(couponId));
        return toResponse(coupon);
    }

    @Override
    public List<CorporateCouponUsageResponse> getUserCorporateCouponUsageHistory(Long userId, Long couponId) {
        CorporateEmployeeCoupon coupon = couponRepository.findById(couponId)
                .filter(c -> c.getUser().getId().equals(userId))
                .orElseThrow(() -> new CorporateCouponNotFoundException(couponId));
        return usageLogRepository.findByCorporateEmployeeCouponId(coupon.getId()).stream()
                .map(log -> new CorporateCouponUsageResponse(log.getId(), log.getBookingId(), log.getSpaId(), log.getUsageDateTime(), log.getSessionNumber(), log.getNotes()))
                .toList();
    }

    private CorporateCouponResponse toResponse(CorporateEmployeeCoupon coupon) {
        CorporateCouponStatus status = coupon.getStatus();
        if (coupon.getExpiryDate() != null && coupon.getExpiryDate().isBefore(java.time.LocalDate.now())) {
            status = CorporateCouponStatus.EXPIRED;
        } else if (coupon.getRemainingSessions() != null && coupon.getRemainingSessions() <= 0) {
            status = CorporateCouponStatus.EXHAUSTED;
        }
        return new CorporateCouponResponse(
                coupon.getId(),
                coupon.getCorporate().getName(),
                coupon.getCouponType(),
                coupon.getTotalSessions(),
                coupon.getRemainingSessions(),
                coupon.getGlobalPackageType(),
                coupon.getStartDate(),
                coupon.getExpiryDate(),
                status
        );
    }
}
