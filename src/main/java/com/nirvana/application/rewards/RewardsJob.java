package com.nirvana.application.rewards;

import com.nirvana.application.service.impl.LoyaltyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Housekeeping for points and gift cards. Each item is handled in its own transaction under a row
 * lock and re-checked there, so the job is safe on several instances at once.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RewardsJob {

    private static final int BATCH = 500;
    /** Desk payments and refunds can land after completion; earned points are re-checked this long. */
    private static final int EARN_RECHECK_DAYS = 7;

    private final LoyaltyLedger ledger;
    private final GiftCardService giftCards;
    private final LoyaltyService loyalty;
    private final TransactionTemplate tx;

    @Scheduled(initialDelayString = "${loyalty.job-interval-ms:3600000}", fixedDelayString = "${loyalty.job-interval-ms:3600000}")
    public void run() {
        expirePoints();
        expireGiftCards();
        recheckEarnedPoints();
    }

    public int expirePoints() {
        int users = 0;
        for (Long userId : ledger.usersWithDueLots(BATCH)) {
            try {
                tx.executeWithoutResult(status -> ledger.lock(userId));
                users++;
            } catch (RuntimeException ex) {
                log.warn("Could not expire points for user {}: {}", userId, ex.getClass().getSimpleName());
            }
        }
        return users;
    }

    public int expireGiftCards() {
        int expired = 0;
        for (Long id : giftCards.dueForExpiry(BATCH)) {
            try {
                if (giftCards.expire(id)) expired++;
            } catch (RuntimeException ex) {
                log.warn("Could not expire gift card {}: {}", id, ex.getClass().getSimpleName());
            }
        }
        return expired;
    }

    public void recheckEarnedPoints() {
        for (Long bookingId : loyalty.recentlyCompleted(EARN_RECHECK_DAYS, 2_000)) {
            try {
                loyalty.syncEarnedForBooking(bookingId);
            } catch (RuntimeException ex) {
                log.warn("Could not re-check points for booking {}: {}", bookingId, ex.getClass().getSimpleName());
            }
        }
    }
}
