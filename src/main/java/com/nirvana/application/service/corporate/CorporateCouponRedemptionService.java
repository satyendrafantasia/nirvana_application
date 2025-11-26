package com.nirvana.application.service.corporate;

import com.nirvana.application.model.dto.corporate.CorporateCouponEligibilityResponse;
import com.nirvana.application.model.dto.corporate.CorporateCouponRedemptionRequest;
import com.nirvana.application.model.dto.corporate.CorporateCouponResponse;

public interface CorporateCouponRedemptionService {
    CorporateCouponEligibilityResponse canUseCorporateCoupon(Long userId, Long spaId);
    CorporateCouponResponse redeemCouponForBooking(Long userId, CorporateCouponRedemptionRequest request);
}
