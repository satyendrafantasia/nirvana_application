package com.nirvana.application;

import com.nirvana.application.model.Coupon;
import com.nirvana.application.model.Service;
import com.nirvana.application.model.Slot;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.User;
import com.nirvana.application.model.enums.DiscountType;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.CouponRepository;
import com.nirvana.application.service.PricingService.PricingResult;
import com.nirvana.application.service.impl.DynamicPricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CouponValidationTest {
    CouponRepository coupons = mock(CouponRepository.class);
    BookingRepository bookings = mock(BookingRepository.class);
    DynamicPricingService pricing = new DynamicPricingService(coupons, bookings);
    Spa spa = mock(Spa.class);
    Service service = mock(Service.class);
    User user = mock(User.class);
    Slot afternoon = mock(Slot.class);
    Coupon coupon;

    @BeforeEach void setUp() {
        when(spa.getId()).thenReturn(1L);
        when(spa.getTimezone()).thenReturn("Asia/Kolkata");
        when(service.getId()).thenReturn(5L);
        when(user.getId()).thenReturn(3L);
        when(user.getLoyaltyPoints()).thenReturn(0);
        // Saturday afternoon in India: no off-peak discount to muddy the coupon arithmetic.
        when(afternoon.getStartTs()).thenReturn(OffsetDateTime.parse("2026-12-05T09:30:00Z"));
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

    PricingResult price(String code, int base) {
        return pricing.evaluatePricing(user, spa, service, afternoon, 1, base, code, false);
    }
    void assertRejected(String expectedText) {
        var problem = assertThrows(ResponseStatusException.class, () -> price("SAVE10", 10000));
        assertEquals(400, problem.getStatusCode().value());
        assertTrue(problem.getReason().contains(expectedText), problem.getReason());
    }

    @Test void unrecognizedCouponStopsCheckoutBeforePayment() {
        var problem = assertThrows(ResponseStatusException.class, () -> price("unknown", 10000));
        assertEquals(400, problem.getStatusCode().value());
    }

    @Test void formerlyHardCodedCodesNoLongerWorkUnlessTheyExistAsCoupons() {
        assertThrows(ResponseStatusException.class, () -> price("WELCOME10", 10000));
        assertThrows(ResponseStatusException.class, () -> price("FESTIVE20", 10000));
    }

    @Test void percentageCouponIsAppliedAndReturnedForTheBooking() {
        var result = price(" SAVE10 ", 10000);
        assertEquals(1000, result.discountCents());
        assertEquals("SAVE10", result.appliedCouponCode());
        assertSame(coupon, result.coupon());
    }

    @Test void percentageCouponRespectsItsCap() {
        coupon.setMaxDiscountCents(300);
        assertEquals(300, price("SAVE10", 10000).discountCents());
    }

    @Test void flatCouponNeverExceedsThePrice() {
        coupon.setDiscountType(DiscountType.FLAT);
        coupon.setDiscountValue(2500);
        assertEquals(2500, price("SAVE10", 10000).discountCents());
        assertEquals(1800, price("SAVE10", 1800).discountCents());
    }

    @Test void inactiveCouponIsRejected() { coupon.setActive(false); assertRejected("not valid"); }

    @Test void expiredCouponIsRejected() { coupon.setValidTo(OffsetDateTime.now().minusMinutes(1)); assertRejected("expired"); }

    @Test void couponIsRejectedBeforeItsStartDate() { coupon.setValidFrom(OffsetDateTime.now().plusHours(1)); assertRejected("not active yet"); }

    @Test void couponForAnotherSpaIsRejected() {
        Spa other = mock(Spa.class);
        when(other.getId()).thenReturn(2L);
        coupon.setSpa(other);
        assertRejected("this spa");
    }

    @Test void couponLimitedToOtherTreatmentsIsRejected() {
        coupon.setAppliesToServicesJson("[1,2]");
        assertRejected("this treatment");
        coupon.setAppliesToServicesJson("[1,5]");
        assertEquals(1000, price("SAVE10", 10000).discountCents());
        coupon.setAppliesToServicesJson("not json");
        assertRejected("this treatment");
    }

    @Test void minimumSpendIsEnforced() { coupon.setMinOrderCents(10001); assertRejected("higher booking value"); }

    @Test void totalUsageLimitIsEnforced() {
        coupon.setMaxUsesGlobal(100);
        when(bookings.countCouponUses(9L)).thenReturn(99L);
        assertEquals(1000, price("SAVE10", 10000).discountCents());
        when(bookings.countCouponUses(9L)).thenReturn(100L);
        assertRejected("fully redeemed");
    }

    @Test void perCustomerLimitIsEnforced() {
        coupon.setMaxUsesPerUser(1);
        when(bookings.countCouponUsesByUser(9L, 3L)).thenReturn(1L);
        assertRejected("already used");
    }

    @Test void offPeakDiscountFollowsTheSpaClockNotUtc() {
        Slot mondayMorningInIndia = mock(Slot.class); // 08:30 IST, 03:00 UTC
        when(mondayMorningInIndia.getStartTs()).thenReturn(OffsetDateTime.parse("2026-12-07T03:00:00Z"));
        Slot mondayAfternoonInIndia = mock(Slot.class); // 13:30 IST, but 08:00 UTC
        when(mondayAfternoonInIndia.getStartTs()).thenReturn(OffsetDateTime.parse("2026-12-07T08:00:00Z"));
        assertEquals(500, pricing.evaluatePricing(user, spa, service, mondayMorningInIndia, 1, 10000, null, false).discountCents());
        assertEquals(0, pricing.evaluatePricing(user, spa, service, mondayAfternoonInIndia, 1, 10000, null, false).discountCents());
    }

    @Test void loyaltyPointsAreWorthARupeeAndCappedAtAShareOfTheBooking() {
        when(user.getLoyaltyPoints()).thenReturn(5000);
        // Rs 100 booking: points may pay 20%, so 20 points worth Rs 20.
        var result = pricing.evaluatePricing(user, spa, service, afternoon, 1, 10000, null, true);
        assertEquals(20, result.loyaltyPointsRedeemed());
        assertEquals(2000, result.discountCents());
    }

    @Test void loyaltyPointsAreLimitedByTheBalance() {
        when(user.getLoyaltyPoints()).thenReturn(7);
        var result = pricing.evaluatePricing(user, spa, service, afternoon, 1, 10000, null, true);
        assertEquals(7, result.loyaltyPointsRedeemed());
        assertEquals(700, result.discountCents());
    }

    @Test void pointsApplyAfterTheCouponOnWhatIsLeft() {
        when(user.getLoyaltyPoints()).thenReturn(5000);
        // Rs 100 less 10% coupon = Rs 90 left; 20% of that is Rs 18.
        var result = pricing.evaluatePricing(user, spa, service, afternoon, 1, 10000, "SAVE10", true);
        assertEquals(18, result.loyaltyPointsRedeemed());
        assertEquals(1000 + 1800, result.discountCents());
    }

    @Test void personalCouponOnlyWorksForItsOwner() {
        coupon.setMetaJson("{\"ownerUserId\":99}");
        assertRejected("another account");
        coupon.setMetaJson("{\"ownerUserId\":3}");
        assertEquals(1000, price("SAVE10", 10000).discountCents());
    }
}
