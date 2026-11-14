package com.nirvana.application.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.model.Coupon;
import com.nirvana.application.model.Slot;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.User;
import com.nirvana.application.model.enums.DiscountType;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.CouponRepository;
import com.nirvana.application.service.PricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class DynamicPricingService implements PricingService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_LOYALTY_REDEMPTION_CENTS = 1_500; // cap redemption per booking
    private static final int MAX_OFF_PEAK_DISCOUNT_CENTS = 1_000;

    private final CouponRepository couponRepository;
    private final BookingRepository bookingRepository;

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
        Coupon coupon = null;
        if (couponCode != null && !couponCode.isBlank()) {
            coupon = requireUsableCoupon(couponCode.trim(), user, spa, service, basePriceCents);
            discountCents += couponDiscount(coupon, basePriceCents);
        }

        discountCents += evaluateOffPeakDiscount(slot, spa, basePriceCents - discountCents);

        int loyaltyRedeemed = 0;
        if (redeemLoyaltyPoints) {
            loyaltyRedeemed = redeemLoyaltyPoints(user, basePriceCents - discountCents);
            discountCents += loyaltyRedeemed;
        }

        discountCents = clampDiscount(basePriceCents, discountCents);

        return new PricingResult(discountCents, coupon != null ? coupon.getCode() : null, loyaltyRedeemed, coupon);
    }

    /**
     * Looks the code up and checks every rule on it. The row stays locked until the booking
     * transaction ends, so usage limits hold even when two people check out at the same moment.
     */
    private Coupon requireUsableCoupon(String code, User user, Spa spa,
                                       com.nirvana.application.model.Service service, int basePriceCents) {
        Coupon coupon = couponRepository.findByCodeForUpdate(code).orElse(null);
        if (coupon == null || !coupon.isActive()) {
            throw rejected("Coupon code is not valid. Check it or remove it to continue.");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (coupon.getValidFrom() != null && now.isBefore(coupon.getValidFrom())) {
            throw rejected("This coupon is not active yet.");
        }
        if (coupon.getValidTo() != null && now.isAfter(coupon.getValidTo())) {
            throw rejected("This coupon has expired.");
        }
        if (coupon.getSpa() != null && (spa == null || !coupon.getSpa().getId().equals(spa.getId()))) {
            throw rejected("This coupon cannot be used at this spa.");
        }
        if (!appliesToService(coupon, service)) {
            throw rejected("This coupon cannot be used for this treatment.");
        }
        if (coupon.getMinOrderCents() != null && basePriceCents < coupon.getMinOrderCents()) {
            throw rejected("This coupon needs a higher booking value.");
        }
        if (coupon.getMaxUsesGlobal() != null
                && bookingRepository.countCouponUses(coupon.getId()) >= coupon.getMaxUsesGlobal()) {
            throw rejected("This coupon has been fully redeemed.");
        }
        if (coupon.getMaxUsesPerUser() != null && user != null
                && bookingRepository.countCouponUsesByUser(coupon.getId(), user.getId()) >= coupon.getMaxUsesPerUser()) {
            throw rejected("You have already used this coupon.");
        }
        return coupon;
    }

    private boolean appliesToService(Coupon coupon, com.nirvana.application.model.Service service) {
        String json = coupon.getAppliesToServicesJson();
        if (json == null || json.isBlank()) {
            return true;
        }
        try {
            JsonNode ids = MAPPER.readTree(json);
            if (ids == null || ids.isNull() || (ids.isArray() && ids.isEmpty())) {
                return true;
            }
            if (!ids.isArray() || service == null) {
                return false;
            }
            for (JsonNode id : ids) {
                if (id.asLong() == service.getId()) {
                    return true;
                }
            }
            return false;
        } catch (Exception unreadable) {
            log.warn("Coupon {} has an unreadable service restriction; refusing it", coupon.getId());
            return false;
        }
    }

    private int couponDiscount(Coupon coupon, int basePriceCents) {
        int value = coupon.getDiscountValue() == null ? 0 : coupon.getDiscountValue();
        int discount = coupon.getDiscountType() == DiscountType.FLAT
                ? value
                : (int) Math.round(basePriceCents * (Math.min(value, 100) / 100.0));
        if (coupon.getMaxDiscountCents() != null) {
            discount = Math.min(discount, coupon.getMaxDiscountCents());
        }
        return Math.max(0, Math.min(discount, basePriceCents));
    }

    /** Weekday mornings in the spa's own time zone. */
    private int evaluateOffPeakDiscount(Slot slot, Spa spa, int remainingBaseCents) {
        if (slot == null || slot.getStartTs() == null) {
            return 0;
        }
        ZonedDateTime start = slot.getStartTs().atZoneSameInstant(spaZone(spa));
        boolean isWeekday = start.getDayOfWeek().getValue() < DayOfWeek.SATURDAY.getValue();
        boolean isMorning = start.getHour() < 12;
        if (isWeekday && isMorning) {
            int calculated = (int) Math.round(remainingBaseCents * 0.05); // 5% off-peak
            return Math.max(0, Math.min(calculated, MAX_OFF_PEAK_DISCOUNT_CENTS));
        }
        return 0;
    }

    private ZoneId spaZone(Spa spa) {
        if (spa != null && spa.getTimezone() != null && !spa.getTimezone().isBlank()) {
            try {
                return ZoneId.of(spa.getTimezone());
            } catch (RuntimeException invalid) {
                // fall through
            }
        }
        return ZoneOffset.UTC;
    }

    private int redeemLoyaltyPoints(User user, int remainingBaseCents) {
        Integer points = user == null ? null : user.getLoyaltyPoints();
        if (points == null || points <= 0) {
            return 0;
        }
        int redeemable = Math.min(points, MAX_LOYALTY_REDEMPTION_CENTS);
        return Math.max(0, Math.min(redeemable, remainingBaseCents));
    }

    private int clampDiscount(int basePriceCents, int discountCents) {
        if (discountCents < 0) {
            return 0;
        }
        return Math.min(discountCents, basePriceCents);
    }

    private ResponseStatusException rejected(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
