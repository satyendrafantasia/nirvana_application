package com.nirvana.application;

import com.nirvana.application.model.Service;
import com.nirvana.application.model.Spa;
import com.nirvana.application.rewards.CheckoutMath;
import com.nirvana.application.rewards.RewardRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CheckoutMathTest {
    final RewardRules rules = RewardRules.defaults();
    final Spa spa = new Spa();
    final Service service = new Service();

    CheckoutMathTest() {
        spa.setGstin("09AABCF1234B1ZA");
        spa.setTaxPercent(18);
    }

    @Test void pointsAreCappedByShareAndBalanceAndCountWholeRupees() {
        assertEquals(20, CheckoutMath.pointsToRedeem(5_000, 10_000, rules), "20% of Rs 100");
        assertEquals(7, CheckoutMath.pointsToRedeem(7, 10_000, rules), "the balance");
        assertEquals(0, CheckoutMath.pointsToRedeem(5_000, 499, rules), "20% of Rs 4.99 is less than one point");
        assertEquals(0, CheckoutMath.pointsToRedeem(0, 10_000, rules));
        assertEquals(0, CheckoutMath.pointsToRedeem(100, 0, rules));
        rules.maxRedeemPercent = 0;
        assertEquals(0, CheckoutMath.pointsToRedeem(100, 10_000, rules), "redemption switched off");
    }

    @Test void earningCountsWholeHundredsOfRupees() {
        assertEquals(0, rules.pointsEarnedFor(9_999));
        assertEquals(1, rules.pointsEarnedFor(10_000));
        assertEquals(13, rules.pointsEarnedFor(134_520));
        rules.pointsPer100Rupees = 0;
        assertEquals(0, rules.pointsEarnedFor(134_520));
    }

    @Test void giftCardValidityIsNeverUnderAYear() {
        rules.giftCardValidityDays = 90;
        assertEquals(365, rules.giftCardValidityDays());
        rules.giftCardValidityDays = 730;
        assertEquals(730, rules.giftCardValidityDays());
    }

    @Test void breakdownAppliesDiscountsThenTaxThenGiftCard() {
        // Rs 120, Rs 6 off-peak, 22 points (Rs 22), 18% tax, Rs 50 gift card.
        var b = CheckoutMath.breakdown(spa, service, 12_000, 600, 22, 2_200, 5_000);
        assertEquals(2_800, b.discountPaise());
        assertEquals(1_656, b.taxPaise(), "tax on 9,200");
        assertEquals(10_856, b.totalPaise());
        assertEquals(5_000, b.giftCardPaise());
        assertEquals(5_856, b.payablePaise());
    }

    @Test void nothingGoesBelowZero() {
        var covered = CheckoutMath.breakdown(spa, service, 12_000, 600, 0, 0, 1_000_000);
        assertEquals(covered.totalPaise(), covered.giftCardPaise(), "a gift card pays at most the total");
        assertEquals(0, covered.payablePaise());
        var overDiscounted = CheckoutMath.breakdown(spa, service, 1_000, 5_000, 0, 0, 0);
        assertEquals(1_000, overDiscounted.discountPaise());
        assertEquals(0, overDiscounted.taxPaise());
        assertEquals(0, overDiscounted.totalPaise());
        assertEquals(0, CheckoutMath.giftCardShare(-5, 100));
        assertEquals(0, CheckoutMath.bookingTax(spa, service, -100));
    }
}
