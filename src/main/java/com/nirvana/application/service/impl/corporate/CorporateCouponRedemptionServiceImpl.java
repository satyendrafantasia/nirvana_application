package com.nirvana.application.service.impl.corporate;

import com.nirvana.application.exception.*;
import com.nirvana.application.model.Booking;
import com.nirvana.application.model.corporate.CorporateEmployeeCoupon;
import com.nirvana.application.model.corporate.CorporateCouponUsageLog;
import com.nirvana.application.model.dto.corporate.CorporateCouponEligibilityResponse;
import com.nirvana.application.model.dto.corporate.CorporateCouponRedemptionRequest;
import com.nirvana.application.model.dto.corporate.CorporateCouponResponse;
import com.nirvana.application.model.enums.CorporateCouponStatus;
import com.nirvana.application.model.enums.PaymentSourceType;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.corporate.CorporateCouponUsageLogRepository;
import com.nirvana.application.repository.corporate.CorporateEmployeeCouponRepository;
import com.nirvana.application.service.corporate.CorporateCouponRedemptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
@Transactional
public class CorporateCouponRedemptionServiceImpl implements CorporateCouponRedemptionService {

    private final CorporateEmployeeCouponRepository couponRepository;
    private final CorporateCouponUsageLogRepository usageLogRepository;
    private final BookingRepository bookingRepository;

    @Override
    @Transactional(readOnly = true)
    public CorporateCouponEligibilityResponse canUseCorporateCoupon(Long userId, Long spaId) {
        return couponRepository.findByUserIdAndStatusAndExpiryDateAfter(userId, CorporateCouponStatus.ACTIVE, LocalDate.now())
                .stream()
                .filter(coupon -> coupon.getRemainingSessions() == null || coupon.getRemainingSessions() > 0)
                .min(Comparator.comparing(CorporateEmployeeCoupon::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(coupon -> new CorporateCouponEligibilityResponse(true, "Coupon available"))
                .orElseGet(() -> new CorporateCouponEligibilityResponse(false, "No active corporate coupon"));
    }

    @Override
    public CorporateCouponResponse redeemCouponForBooking(Long userId, CorporateCouponRedemptionRequest request) {
        CorporateEmployeeCoupon coupon = resolveCoupon(userId, request.couponId());
        validateCoupon(coupon);
        Booking booking = bookingRepository.findById(request.bookingId())
                .orElseThrow(() -> new NotFoundException("Booking not found: " + request.bookingId()));
        coupon.setRemainingSessions(coupon.getRemainingSessions() != null ? coupon.getRemainingSessions() - 1 : null);
        if (coupon.getRemainingSessions() != null && coupon.getRemainingSessions() <= 0) {
            coupon.setStatus(CorporateCouponStatus.EXHAUSTED);
        }
        CorporateCouponUsageLog log = CorporateCouponUsageLog.builder()
                .corporateEmployeeCoupon(coupon)
                .corporate(coupon.getCorporate())
                .user(coupon.getUser())
                .bookingId(request.bookingId())
                .spaId(request.spaId())
                .usageDateTime(OffsetDateTime.now())
                .sessionNumber(coupon.getTotalSessions() != null ? coupon.getTotalSessions() - coupon.getRemainingSessions() : 1)
                .build();
        booking.setCorporateEmployeeCoupon(coupon);
        booking.setPaymentSourceType(PaymentSourceType.CORPORATE_COUPON);
        usageLogRepository.save(log);
        return new CorporateCouponResponse(
                coupon.getId(),
                coupon.getCorporate().getName(),
                coupon.getCouponType(),
                coupon.getTotalSessions(),
                coupon.getRemainingSessions(),
                coupon.getGlobalPackageType(),
                coupon.getStartDate(),
                coupon.getExpiryDate(),
                coupon.getStatus()
        );
    }

    private CorporateEmployeeCoupon resolveCoupon(Long userId, Long couponId) {
        if (couponId != null) {
            return couponRepository.findById(couponId)
                    .filter(c -> c.getUser().getId().equals(userId))
                    .orElseThrow(() -> new CorporateCouponNotFoundException(couponId));
        }
        return couponRepository.findByUserIdAndStatusAndExpiryDateAfter(userId, CorporateCouponStatus.ACTIVE, LocalDate.now())
                .stream()
                .filter(coupon -> coupon.getRemainingSessions() == null || coupon.getRemainingSessions() > 0)
                .findFirst()
                .orElseThrow(() -> new NoActiveCorporateCouponException(userId));
    }

    private void validateCoupon(CorporateEmployeeCoupon coupon) {
        if (coupon.getExpiryDate() != null && coupon.getExpiryDate().isBefore(LocalDate.now())) {
            coupon.setStatus(CorporateCouponStatus.EXPIRED);
            throw new CorporateCouponExpiredException(coupon.getId());
        }
        if (coupon.getRemainingSessions() != null && coupon.getRemainingSessions() <= 0) {
            coupon.setStatus(CorporateCouponStatus.EXHAUSTED);
            throw new CorporateCouponExhaustedException(coupon.getId());
        }
    }
}
