package com.nirvana.application.rewards;

import com.nirvana.application.model.Service;
import com.nirvana.application.model.Spa;
import com.nirvana.application.service.gst.GstMath;

import java.math.BigDecimal;

/**
 * The order in which a booking's price is reduced. Every step works on what is left after the
 * one before, and nothing goes below zero:
 * <ol>
 *   <li>coupon, on the treatment price;</li>
 *   <li>off-peak discount, on what is left;</li>
 *   <li>loyalty points, at most {@code maxRedeemPercent} of what is left and never more than the balance;</li>
 *   <li>tax, on the price less those discounts;</li>
 *   <li>gift card, against the total including tax (it is a way to pay, not a discount);</li>
 *   <li>the rest is paid online or at the spa.</li>
 * </ol>
 * Steps 1 and 2 are priced by {@code PricingService}; this class holds the arithmetic for the rest.
 */
public final class CheckoutMath {

    private CheckoutMath() {
    }

    /** Whole points to spend: limited by the balance and by the share of the booking points may pay. */
    public static int pointsToRedeem(int balance, int remainingPaise, RewardRules rules) {
        if (balance <= 0 || remainingPaise <= 0 || rules.pointValuePaise <= 0 || rules.maxRedeemPercent <= 0) return 0;
        long capPaise = (long) remainingPaise * Math.min(100, rules.maxRedeemPercent) / 100;
        long points = Math.min(balance, capPaise / rules.pointValuePaise);
        return (int) Math.max(0, points);
    }

    public static int pointsValue(int points, RewardRules rules) {
        return Math.max(0, points) * Math.max(0, rules.pointValuePaise);
    }

    /** Booking GST uses the service rate when set, otherwise the registered spa's default. */
    public static int bookingTax(Spa spa, Service service, int taxablePaise) {
        if (GstMath.normalizeGstin(spa.getGstin()) == null) return 0;
        BigDecimal rate = service.getGstRatePct();
        if (rate == null && spa.getTaxPercent() != null) {
            rate = BigDecimal.valueOf(spa.getTaxPercent());
        }
        return Math.toIntExact(GstMath.taxOnExclusive(taxablePaise, GstMath.bps(rate)));
    }

    /** The part of the total a gift card pays: all of it when the balance allows, else the balance. */
    public static int giftCardShare(int giftBalance, int totalPaise) {
        return Math.max(0, Math.min(giftBalance, totalPaise));
    }

    public record Breakdown(int pricePaise, int discountPaise, int pointsRedeemed, int pointsPaise,
                            int taxPaise, int totalPaise, int giftCardPaise, int payablePaise) {
    }

    /**
     * @param otherDiscountPaise coupon and off-peak discounts already priced
     * @param pointsPaise        value of the points being spent (already capped)
     */
    public static Breakdown breakdown(Spa spa, Service service, int pricePaise, int otherDiscountPaise,
                                      int pointsRedeemed, int pointsPaise, int giftBalance) {
        int price = Math.max(0, pricePaise);
        int discount = Math.min(price, Math.max(0, otherDiscountPaise) + Math.max(0, pointsPaise));
        int tax = bookingTax(spa, service, price - discount);
        int total = Math.max(0, price - discount + tax);
        int gift = giftCardShare(giftBalance, total);
        return new Breakdown(price, discount, pointsRedeemed, pointsPaise, tax, total, gift, total - gift);
    }
}
