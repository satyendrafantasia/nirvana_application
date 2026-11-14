package com.nirvana.application.service.impl;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.enums.BookingPaymentType;
import com.nirvana.application.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Loyalty points: one point is worth one minor currency unit at checkout. Points are earned when
 * an appointment is completed and handed back when a booking that spent them is cancelled.
 * Both amounts are recorded on the booking, so neither can happen twice.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoyaltyService {

    private final UserRepository userRepository;

    /** Percentage of the amount paid that comes back as points. 0 switches earning off. */
    @Value("${loyalty.earn-percent:2}")
    private int earnPercent;

    /** Gives back the points this booking spent. The caller saves the booking. */
    public int returnRedeemed(Booking booking) {
        int points = booking.getLoyaltyPointsRedeemed() == null ? 0 : booking.getLoyaltyPointsRedeemed();
        if (points <= 0) {
            return 0;
        }
        userRepository.addLoyaltyPoints(booking.getUser().getId(), points);
        booking.setLoyaltyPointsRedeemed(0);
        log.info("Returned {} loyalty point(s) for cancelled booking {}", points, booking.getId());
        return points;
    }

    /** Awards points for a completed booking. The caller saves the booking. */
    public int awardFor(Booking booking) {
        boolean alreadyAwarded = booking.getLoyaltyPointsEarned() != null && booking.getLoyaltyPointsEarned() > 0;
        if (earnPercent <= 0 || alreadyAwarded || booking.getPaymentType() == BookingPaymentType.PAID_BY_PACKAGE) {
            return 0;
        }
        long paidCents = (long) nz(booking.getPriceCents()) + nz(booking.getTaxCents()) - nz(booking.getDiscountCents());
        int points = (int) Math.max(0, paidCents * earnPercent / 100);
        if (points <= 0) {
            return 0;
        }
        userRepository.addLoyaltyPoints(booking.getUser().getId(), points);
        booking.setLoyaltyPointsEarned(points);
        return points;
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
