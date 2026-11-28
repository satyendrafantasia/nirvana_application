package com.nirvana.application;

import com.nirvana.application.model.Coupon;
import com.nirvana.application.model.Service;
import com.nirvana.application.model.Slot;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.User;
import com.nirvana.application.model.enums.DiscountType;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.CouponRepository;
import com.nirvana.application.rewards.CheckoutMath;
import com.nirvana.application.rewards.RewardRules;
import com.nirvana.application.service.impl.DynamicPricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Coupons and the off-peak discount are on the treatment only; add-ons count towards a minimum order and points may pay for them. */
class AddOnPricingTest {
    CouponRepository coupons = mock(CouponRepository.class);
    BookingRepository bookings = mock(BookingRepository.class);
    DynamicPricingService pricing = new DynamicPricingService(coupons, bookings);
    Spa spa = mock(Spa.class);
    Service service = mock(Service.class);
    User user = mock(User.class);
    Slot afternoon = mock(Slot.class), morning = mock(Slot.class);
    Coupon coupon;

    @BeforeEach void setUp() {
        when(spa.getId()).thenReturn(1L);
        when(spa.getTimezone()).thenReturn("Asia/Kolkata");
        when(service.getId()).thenReturn(5L);
        when(user.getId()).thenReturn(3L);
        when(user.getLoyaltyPoints()).thenReturn(0);
        when(afternoon.getStartTs()).thenReturn(OffsetDateTime.parse("2026-12-05T09:30:00Z")); // Saturday 15:00 IST
        when(morning.getStartTs()).thenReturn(OffsetDateTime.parse("2026-12-07T04:30:00Z"));   // Monday 10:00 IST
        coupon = new Coupon();
        coupon.setId(9L);
        coupon.setCode("SAVE10");
        coupon.setActive(true);
        coupon.setDiscountType(DiscountType.PERCENTAGE);
        coupon.setDiscountValue(10);
        coupon.setValidFrom(OffsetDateTime.now().minusDays(1));
        coupon.setValidTo(OffsetDateTime.now().plusDays(1));
        when(coupons.findByCodeForUpdate("SAVE10")).thenReturn(Optional.of(coupon));
    }

    @Test void aPercentageCouponDiscountsTheTreatmentButNotTheAddOns() {
        var result = pricing.evaluatePricing(user, spa, service, afternoon, 1, 10_000, 5_000, "SAVE10", false);
        assertEquals(1_000, result.discountCents(), "10% of the 10,000 treatment, not of 15,000");
    }

    @Test void addOnsCountTowardsTheCouponsMinimumOrder() {
        coupon.setMinOrderCents(12_000);
        assertThrows(ResponseStatusException.class,
                () -> pricing.evaluatePricing(user, spa, service, afternoon, 1, 10_000, 0, "SAVE10", false));
        assertEquals(1_000, pricing.evaluatePricing(user, spa, service, afternoon, 1, 10_000, 5_000, "SAVE10", false).discountCents());
    }

    @Test void theOffPeakDiscountIsOnTheTreatmentOnly() {
        // 5% of 10,000 is 500; 5% of 60,000 would be capped at 1,000.
        assertEquals(500, pricing.evaluatePricing(user, spa, service, morning, 1, 10_000, 50_000, null, false).discountCents());
    }

    @Test void loyaltyPointsMayPayForAddOnsToo() {
        when(user.getLoyaltyPoints()).thenReturn(100_000);
        var rules = RewardRules.defaults();
        var result = pricing.evaluatePricing(user, spa, service, afternoon, 1, 10_000, 20_000, null, true);
        int expected = CheckoutMath.pointsToRedeem(100_000, 30_000, rules);
        assertEquals(expected, result.loyaltyPointsRedeemed());
        assertTrue(CheckoutMath.pointsValue(expected, rules) > CheckoutMath.pointsValue(CheckoutMath.pointsToRedeem(100_000, 10_000, rules), rules),
                "the cap is on the whole booking, add-ons included");
        assertEquals(CheckoutMath.pointsValue(expected, rules), result.discountCents());
    }

    @Test void withoutAddOnsPricingIsUnchanged() {
        assertEquals(pricing.evaluatePricing(user, spa, service, afternoon, 1, 10_000, "SAVE10", false),
                pricing.evaluatePricing(user, spa, service, afternoon, 1, 10_000, 0, "SAVE10", false));
    }
}
