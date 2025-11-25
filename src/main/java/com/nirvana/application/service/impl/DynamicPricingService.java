package com.nirvana.application.service.impl;

import com.nirvana.application.model.Slot;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.User;
import com.nirvana.application.service.PricingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Service
@Slf4j
public class DynamicPricingService implements PricingService {

    private static final Map<String, Integer> COUPON_DISCOUNTS = Map.of(
            "WELCOME10", 10,
            "FESTIVE20", 20
    );

    private static final int MAX_COUPON_DISCOUNT_CENTS = 2_000; // cap coupon value
    private static final int MAX_LOYALTY_REDEMPTION_CENTS = 1_500; // cap redemption per booking

    @Override
    public PricingResult evaluatePricing(User user,
                                         Spa spa,
                                         com.nirvana.application.model.Service service,
                                         Slot slot,
                                         int guestCount,
                                         int basePriceCents,
                                         String couponCode,
                                         boolean redeemLoyaltyPoints) {
        int discountCents = 0;
        String appliedCoupon = null;
        if (couponCode != null && !couponCode.isBlank()) {
            appliedCoupon = couponCode.trim().toUpperCase();
            discountCents += evaluateCouponDiscount(appliedCoupon, basePriceCents);
        }

        discountCents += evaluateOffPeakDiscount(slot, basePriceCents - discountCents);

        int loyaltyRedeemed = 0;
        if (redeemLoyaltyPoints) {
            loyaltyRedeemed = redeemLoyaltyPoints(user, basePriceCents - discountCents);
            discountCents += loyaltyRedeemed;
        }

        discountCents = clampDiscount(basePriceCents, discountCents);

        log.debug("Pricing evaluated for user {} with coupon {}: discount {} cents (loyalty {})",
                user.getId(), appliedCoupon, discountCents, loyaltyRedeemed);

        return new PricingResult(discountCents, appliedCoupon, loyaltyRedeemed);
    }

    private int evaluateCouponDiscount(String couponCode, int basePriceCents) {
        Integer percent = COUPON_DISCOUNTS.get(couponCode);
        if (percent == null || percent <= 0) {
            return 0;
        }
        int calculated = (int) Math.round(basePriceCents * (percent / 100.0));
        return Math.min(calculated, MAX_COUPON_DISCOUNT_CENTS);
    }

    private int evaluateOffPeakDiscount(Slot slot, int remainingBaseCents) {
        OffsetDateTime start = slot.getStartTs().withOffsetSameInstant(ZoneOffset.UTC);
        boolean isWeekday = start.getDayOfWeek().getValue() < DayOfWeek.SATURDAY.getValue();
        boolean isMorning = start.getHour() < 12;
        if (isWeekday && isMorning) {
            int calculated = (int) Math.round(remainingBaseCents * 0.05); // 5% off-peak
            return Math.min(calculated, 1_000);
        }
        return 0;
    }

    private int redeemLoyaltyPoints(User user, int remainingBaseCents) {
        Integer points = user.getLoyaltyPoints();
        if (points == null || points <= 0) {
            return 0;
        }
        int redeemable = Math.min(points, MAX_LOYALTY_REDEMPTION_CENTS);
        return Math.min(redeemable, remainingBaseCents);
    }

    private int clampDiscount(int basePriceCents, int discountCents) {
        if (discountCents < 0) {
            return 0;
        }
        return Math.min(discountCents, basePriceCents);
    }
}
