package com.nirvana.application.service.corporate;

import com.nirvana.application.model.dto.corporate.CorporateCouponResponse;
import com.nirvana.application.model.dto.corporate.CorporateCouponUsageResponse;

import java.util.List;

public interface CorporateCouponService {
    List<CorporateCouponResponse> getUserCorporateCoupons(Long userId);
    CorporateCouponResponse getUserCorporateCouponDetails(Long userId, Long couponId);
    List<CorporateCouponUsageResponse> getUserCorporateCouponUsageHistory(Long userId, Long couponId);
}
