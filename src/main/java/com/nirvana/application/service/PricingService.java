package com.nirvana.application.service;

import com.nirvana.application.model.Coupon;
import com.nirvana.application.model.Slot;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.User;

/**
 * Encapsulates promo code validation, loyalty redemptions, and contextual discounts (e.g. off-peak).
 */
public interface PricingService {

    /** A booking without add-ons. */
    default PricingResult evaluatePricing(User user,
                                          Spa spa,
                                          com.nirvana.application.model.Service service,
                                          Slot slot,
                                          int guestCount,
                                          int basePriceCents,
                                          String couponCode,
                                          boolean redeemLoyaltyPoints) {
        return evaluatePricing(user, spa, service, slot, guestCount, basePriceCents, 0, couponCode, redeemLoyaltyPoints);
    }

    /**
     * @param basePriceCents the treatment price for all guests; coupons and the off-peak discount apply to this only
     * @param addOnCents     the chosen add-ons; they count towards a coupon's minimum order, and points may pay for them
     */
    PricingResult evaluatePricing(User user,
                                  Spa spa,
                                  com.nirvana.application.model.Service service,
                                  Slot slot,
                                  int guestCount,
                                  int basePriceCents,
                                  int addOnCents,
                                  String couponCode,
                                  boolean redeemLoyaltyPoints);

    /**
     * @param coupon the validated coupon that was applied, or null when no code was used
     */
    record PricingResult(int discountCents, String appliedCouponCode, int loyaltyPointsRedeemed, Coupon coupon) {
    }
}
