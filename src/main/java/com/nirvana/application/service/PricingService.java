package com.nirvana.application.service;

import com.nirvana.application.model.Slot;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.User;

/**
 * Encapsulates promo code validation, loyalty redemptions, and contextual discounts (e.g. off-peak).
 */
public interface PricingService {

    PricingResult evaluatePricing(User user,
                                  Spa spa,
                                  com.nirvana.application.model.Service service,
                                  Slot slot,
                                  int guestCount,
                                  int basePriceCents,
                                  String couponCode,
                                  boolean redeemLoyaltyPoints);

    record PricingResult(int discountCents, String appliedCouponCode, int loyaltyPointsRedeemed) {
    }
}
