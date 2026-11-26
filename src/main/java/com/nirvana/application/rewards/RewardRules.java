package com.nirvana.application.rewards;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The configurable numbers behind loyalty points, referrals and gift cards (see application.yaml).
 * Plain fields so tests can build one with {@link #defaults()} and change what they need.
 */
@Component
public class RewardRules {

    public enum ReferralReward { POINTS, COUPON }

    /** RBI: gift instruments must stay valid for at least one year. */
    public static final int MIN_GIFT_CARD_VALIDITY_DAYS = 365;

    @Value("${loyalty.points-per-100-rupees:1}") public int pointsPer100Rupees = 1;
    @Value("${loyalty.point-value-paise:100}") public int pointValuePaise = 100;
    @Value("${loyalty.max-redeem-percent:20}") public int maxRedeemPercent = 20;
    @Value("${loyalty.expiry-months:12}") public int expiryMonths = 12;

    @Value("${referral.reward-type:POINTS}") public ReferralReward referralReward = ReferralReward.POINTS;
    @Value("${referral.referrer-points:100}") public int referrerPoints = 100;
    @Value("${referral.referee-points:100}") public int refereePoints = 100;
    @Value("${referral.coupon-value-paise:10000}") public int referralCouponPaise = 10_000;
    @Value("${referral.coupon-valid-days:90}") public int referralCouponDays = 90;
    @Value("${referral.monthly-cap:5}") public int referralMonthlyCap = 5;
    @Value("${referral.claim-window-days:30}") public int referralClaimWindowDays = 30;

    @Value("${gift-cards.min-paise:50000}") public int giftCardMinPaise = 50_000;
    @Value("${gift-cards.max-paise:1000000}") public int giftCardMaxPaise = 1_000_000;
    @Value("${gift-cards.validity-days:365}") public int giftCardValidityDays = 365;

    public static RewardRules defaults() {
        return new RewardRules();
    }

    /** Points for an amount paid: whole hundreds of rupees times the rate. */
    public int pointsEarnedFor(long paidPaise) {
        if (pointsPer100Rupees <= 0 || paidPaise <= 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, (paidPaise / 10_000) * pointsPer100Rupees);
    }

    public int giftCardValidityDays() {
        return Math.max(MIN_GIFT_CARD_VALIDITY_DAYS, giftCardValidityDays);
    }
}
