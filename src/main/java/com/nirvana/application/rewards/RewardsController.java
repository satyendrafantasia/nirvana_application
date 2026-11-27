package com.nirvana.application.rewards;

import com.nirvana.application.model.Slot;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.User;
import com.nirvana.application.repository.ServiceRepository;
import com.nirvana.application.repository.SlotRepository;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.repository.UserRepository;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.PricingService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** The signed-in customer's points, referral code and a price preview of their checkout. */
@RestController
@RequiredArgsConstructor
public class RewardsController {

    private final LoyaltyLedger ledger;
    private final RewardRules rules;
    private final ReferralService referrals;
    private final GiftCardService giftCards;
    private final BookingWallet wallet;
    private final PricingService pricing;
    private final UserRepository users;
    private final SpaRepository spas;
    private final ServiceRepository services;
    private final SlotRepository slots;

    public record Loyalty(int balance, int pointValuePaise, int maxRedeemPercent, int pointsPer100Rupees, int expiryMonths,
                          int expiringSoonPoints, Instant expiringSoonAt, List<LoyaltyLedger.Entry> history) {
    }

    @GetMapping("/api/me/loyalty")
    @Transactional
    public Loyalty loyalty() {
        long userId = SecurityUtils.getCurrentUserId();
        ledger.lock(userId); // brings expiries up to date before showing the balance
        Map<String, Object> soon = ledger.expiringWithin(userId, 30);
        Object at = soon.get("firstAt");
        return new Loyalty(ledger.balanceOf(userId), rules.pointValuePaise, rules.maxRedeemPercent, rules.pointsPer100Rupees,
                rules.expiryMonths, ((Number) soon.get("points")).intValue(), at == null ? null : LoyaltyLedger.utc(at),
                ledger.history(userId, 100));
    }

    @GetMapping("/api/me/referral")
    public ReferralService.Summary referral() {
        return referrals.summary(SecurityUtils.getCurrentUserId());
    }

    public record Claim(@NotNull @Size(min = 4, max = 16) String code) {
    }

    @PostMapping("/api/me/referral/claim")
    public ReferralService.Summary claim(@Valid @RequestBody Claim input) {
        return referrals.claim(SecurityUtils.getCurrentUserId(), input.code());
    }

    public record PreviewInput(@NotNull Long spaId, @NotNull Long serviceId, Long slotId, @NotNull @Min(1) @Max(20) Integer guests,
                               @Size(max = 64) String couponCode, Boolean redeemLoyaltyPoints, @Size(max = 32) String giftCardCode, Boolean useWalletCredit) {
    }

    /**
     * Everything the checkout will charge, line by line, before anything is booked or spent.
     * A coupon or gift card that cannot be used is left out and explained instead of failing.
     */
    /** discountCents is coupon plus off-peak; points are shown on their own line. */
    public record Preview(String currency, int priceCents, int discountCents, String couponCode, String couponError,
                         int pointsRedeemed, int pointsCents, int pointsBalance, int taxCents,
                         int totalCents, int giftCardCents, int giftCardBalanceCents, String giftCardError, int payableCents,
                         int walletCents, int walletBalanceCents, String walletError) {
    }

    @PostMapping("/api/v1/checkout/preview")
    @Transactional
    public Preview preview(@Valid @RequestBody PreviewInput input) {
        long userId = SecurityUtils.getCurrentUserId();
        User user = users.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));
        Spa spa = spas.findById(input.spaId()).orElseThrow(() -> new EntityNotFoundException("Spa not found"));
        var service = services.findByIdAndSpa_Id(input.serviceId(), spa.getId()).orElseThrow(() -> new EntityNotFoundException("Treatment not found"));
        Slot slot = input.slotId() == null ? null : slots.findById(input.slotId()).filter(s -> s.getSpa() != null && s.getSpa().getId().equals(spa.getId())).orElse(null);
        int unit = service.getPriceCents() != null ? service.getPriceCents() : (service.getBasePriceCents() != null ? service.getBasePriceCents() : 0);
        int price = unit * input.guests();
        boolean points = Boolean.TRUE.equals(input.redeemLoyaltyPoints());

        String couponError = null;
        PricingService.PricingResult withCoupon = null;
        if (input.couponCode() != null && !input.couponCode().isBlank()) {
            try {
                withCoupon = pricing.evaluatePricing(user, spa, service, slot, input.guests(), price, input.couponCode(), points);
            } catch (ResponseStatusException rejected) {
                couponError = rejected.getReason();
            }
        }
        PricingService.PricingResult priced = withCoupon != null ? withCoupon
                : pricing.evaluatePricing(user, spa, service, slot, input.guests(), price, null, points);
        int pointsCents = CheckoutMath.pointsValue(priced.loyaltyPointsRedeemed(), rules);
        int otherDiscount = Math.max(0, priced.discountCents() - pointsCents);

        int giftBalance = 0;
        String giftError = null;
        boolean useWallet = Boolean.TRUE.equals(input.useWalletCredit());
        if (useWallet && input.giftCardCode() != null && !input.giftCardCode().isBlank()) throw new IllegalArgumentException("Choose either wallet credit or a gift card");
        if (useWallet || input.giftCardCode() != null && !input.giftCardCode().isBlank()) {
            try {
                giftBalance = useWallet
                        ? wallet.quote(userId, service.getCurrency() != null ? service.getCurrency() : spa.getDefaultCurrency(), Integer.MAX_VALUE)
                        : giftCards.quote(input.giftCardCode(), spa.getId(), Integer.MAX_VALUE);
            } catch (ResponseStatusException rejected) {
                giftError = rejected.getReason();
            }
        }
        var lines = CheckoutMath.breakdown(spa, service, price, otherDiscount,
                priced.loyaltyPointsRedeemed(), pointsCents, giftBalance);
        // Pricing locks the coupon row to count its uses; a preview must not keep that lock or change anything.
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        String currency = service.getCurrency() != null ? service.getCurrency() : spa.getDefaultCurrency();
        return new Preview(currency, price, otherDiscount, priced.appliedCouponCode(), couponError,
                lines.pointsRedeemed(), lines.pointsPaise(), ledger.spendable(userId), lines.taxPaise(), lines.totalPaise(),
                useWallet ? 0 : lines.giftCardPaise(), useWallet ? 0 : giftBalance, useWallet ? null : giftError, lines.payablePaise(),
                useWallet ? lines.giftCardPaise() : 0, useWallet ? giftBalance : 0, useWallet ? giftError : null);
    }
}
