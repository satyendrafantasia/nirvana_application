package com.nirvana.application.service.impl;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.enums.BookingPaymentType;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.rewards.GiftCardService;
import com.nirvana.application.rewards.BookingWallet;
import com.nirvana.application.rewards.LoyaltyLedger;
import com.nirvana.application.rewards.ReferralService;
import com.nirvana.application.rewards.RewardRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * What booking code calls for points, gift cards and referrals; the ledgers live in
 * {@code com.nirvana.application.rewards}.
 * <ul>
 *   <li>Checkout: spends the points priced by PricingService and the gift card share of the total.</li>
 *   <li>Cancellation: points come back in full; the gift card share comes back less any cancellation fee.</li>
 *   <li>Completion: points are earned on what was actually paid (online, at the desk, by gift card),
 *       and kept in step with refunds afterwards. A completed paid booking also settles a pending referral.</li>
 * </ul>
 * Each change is recorded on the booking (points redeemed and earned) and in the ledgers, so none
 * can happen twice.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoyaltyService {

    private final LoyaltyLedger ledger;
    private final GiftCardService giftCards;
    private final BookingWallet wallet;
    private final ReferralService referrals;
    private final RewardRules rules;
    private final JdbcTemplate db;
    private final BookingRepository bookings;

    // ------------------------------------------------------------------ checkout

    /** How much of the total a gift card code pays at this spa; validates the code, spends nothing. */
    public com.nirvana.application.model.dto.CancellationQuote.Tender giftCardReturnQuote(Booking booking, int percent) {
        return booking.getGiftCardCents() != null && booking.getGiftCardCents() > 0 ? giftCards.quoteReturn(booking.getId(), percent)
                : new com.nirvana.application.model.dto.CancellationQuote.Tender(0, 0);
    }

    public int giftCardShare(String code, Spa spa, int totalCents) {
        if (code == null || code.isBlank()) return 0;
        return giftCards.quote(code, spa.getId(), totalCents);
    }

    /**
     * Spends the points and gift card balance used to price a new booking. Fails, rolling the
     * booking back, if either balance changed since it was priced.
     */
    public void redeemAtCheckout(Booking booking, int points, String giftCardCode, int giftCardCents) {
        long userId = booking.getUser().getId();
        if (points > 0) {
            ledger.lock(userId);
            ledger.debit(userId, booking.getId(), LoyaltyLedger.Type.REDEEM, points, true,
                    "redeem:" + booking.getId(), "Used on booking " + booking.getBookingReference(), null);
        }
        if (giftCardCents > 0) {
            int taken = giftCards.redeem(booking.getId(), booking.getSpa().getId(), giftCardCode, giftCardCents);
            if (taken != giftCardCents) {
                throw new IllegalStateException("Your gift card balance has changed. Review your checkout and try again.");
            }
        }
    }

    // ------------------------------------------------------------------ cancellation

    /** Gives back the points and gift card balance this booking spent. The caller saves the booking. */
    public int returnRedeemed(Booking booking) {
        return returnRedeemed(booking, 0);
    }

    /**
     * As {@link #returnRedeemed(Booking)}, keeping {@code feePercent} of the gift card share when the
     * customer cancels late (the same fee their card payment is charged). Points always come back.
     */
    public int returnRedeemed(Booking booking, int feePercent) {
        int points = booking.getLoyaltyPointsRedeemed() == null ? 0 : booking.getLoyaltyPointsRedeemed();
        if (points > 0) {
            long userId = booking.getUser().getId();
            ledger.lock(userId);
            ledger.credit(userId, booking.getId(), LoyaltyLedger.Type.REDEEM_RETURN, points, ledger.returnExpiry(booking.getId()),
                    "redeem-return:" + booking.getId(), "Booking " + booking.getBookingReference() + " was cancelled", null);
            booking.setLoyaltyPointsRedeemed(0);
            log.info("Returned {} loyalty point(s) for cancelled booking {}", points, booking.getId());
        }
        int percent = 100 - Math.max(0, Math.min(100, feePercent));
        wallet.returnForBooking(booking, percent);
        if (booking.getGiftCardCents() != null && booking.getGiftCardCents() > 0) {
            int back = giftCards.returnForBooking(booking.getId(), percent);
            log.info("Returned {} paise to gift card for cancelled booking {}", back, booking.getId());
        }
        return points;
    }

    // ------------------------------------------------------------------ earning

    /** Awards points for a booking that was just completed. The caller saves the booking. */
    public int awardFor(Booking booking) {
        return syncEarned(booking);
    }

    /**
     * Makes the points earned on a booking match what was paid for it and not refunded: credits
     * the difference, or takes it back after a refund. Returns the change. The booking must be locked.
     */
    public int syncEarned(Booking booking) {
        int current = booking.getLoyaltyPointsEarned() == null ? 0 : booking.getLoyaltyPointsEarned();
        boolean earns = booking.getStatus() == BookingStatus.COMPLETED && booking.getPaymentType() != BookingPaymentType.PAID_BY_PACKAGE;
        long paid = earns ? netPaid(booking) : 0;
        int target = earns ? rules.pointsEarnedFor(paid) : 0;
        long userId = booking.getUser().getId();
        int change = 0;
        if (target > current) {
            ledger.lock(userId);
            change = ledger.credit(userId, booking.getId(), LoyaltyLedger.Type.EARN, target - current, ledger.standardExpiry(),
                    null, "Earned on booking " + booking.getBookingReference(), null);
            booking.setLoyaltyPointsEarned(target);
        } else if (target < current) {
            ledger.lock(userId);
            change = -ledger.debit(userId, booking.getId(), LoyaltyLedger.Type.EARN_REVERSAL, current - target, false,
                    null, "Refund on booking " + booking.getBookingReference(), null);
            booking.setLoyaltyPointsEarned(target);
        }
        if (earns && paid > 0) referrals.onPaidBookingCompleted(userId, booking.getId());
        return change;
    }

    /** After a refund of a booking payment (gateway webhook). */
    @Transactional
    public void syncEarnedForPayment(String paymentId) {
        List<Long> ids = db.queryForList("SELECT booking_id FROM payment WHERE transaction_id=?", Long.class, paymentId);
        if (!ids.isEmpty() && ids.get(0) != null) syncEarnedForBooking(ids.get(0));
    }

    @Transactional
    public void syncEarnedForBooking(long bookingId) {
        bookings.findByIdForUpdate(bookingId).ifPresent(booking -> {
            syncEarned(booking);
            bookings.save(booking);
        });
    }

    /** Completed bookings from the last {@code days} whose earned points may be out of step with payments. */
    public List<Long> recentlyCompleted(int days, int limit) {
        return db.queryForList("SELECT id FROM booking WHERE status='COMPLETED' AND COALESCE(payment_type,'')<>'PAID_BY_PACKAGE'"
                + " AND completed_at>=UTC_TIMESTAMP(6) - INTERVAL ? DAY ORDER BY id LIMIT ?", Long.class, days, limit);
    }

    /** Money received for the booking and not given back, capped at its total. */
    long netPaid(Booking booking) {
        long id = booking.getId();
        Long online = db.queryForObject("SELECT COALESCE(SUM(amount_cents-COALESCE(refunded_cents,0)),0) FROM payment WHERE booking_id=?"
                + " AND payment_status IN ('CAPTURED','COMPLETED','PARTIALLY_REFUNDED','REFUNDED')", Long.class, id);
        Long desk = db.queryForObject("SELECT COALESCE(SUM(amount_cents),0) FROM spa_desk_receipt WHERE booking_id=? AND returned_at IS NULL",
                Long.class, id);
        Long vouchers = db.queryForObject("SELECT COALESCE(SUM(amount_cents),0) FROM payment_refund WHERE booking_id=? AND status='COMPLETED'"
                + " AND voucher_code IS NOT NULL", Long.class, id);
        long gift = giftCards.netRedeemed(id) + (long) wallet.netSpent(id);
        long total = (long) nz(booking.getPriceCents()) + nz(booking.getTaxCents()) - nz(booking.getDiscountCents());
        long paid = nz(online) + nz(desk) + gift - nz(vouchers);
        return Math.max(0, Math.min(paid, total));
    }

    private static long nz(Long value) {
        return value == null ? 0 : value;
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
