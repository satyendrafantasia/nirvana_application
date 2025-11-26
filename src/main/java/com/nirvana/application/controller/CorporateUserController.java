package com.nirvana.application.controller;

import com.nirvana.application.model.dto.corporate.*;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.corporate.CorporateCouponRedemptionService;
import com.nirvana.application.service.corporate.CorporateCouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/user/corporate")
@RequiredArgsConstructor
public class CorporateUserController {

    private final CorporateCouponService corporateCouponService;
    private final CorporateCouponRedemptionService redemptionService;

    @GetMapping("/benefits")
    public List<CorporateCouponResponse> listBenefits() {
        Long userId = SecurityUtils.getCurrentUserId();
        return corporateCouponService.getUserCorporateCoupons(userId);
    }

    @GetMapping("/coupons/{couponId}")
    public CorporateCouponResponse getCoupon(@PathVariable Long couponId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return corporateCouponService.getUserCorporateCouponDetails(userId, couponId);
    }

    @GetMapping("/coupons/{couponId}/usage")
    public List<CorporateCouponUsageResponse> getUsage(@PathVariable Long couponId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return corporateCouponService.getUserCorporateCouponUsageHistory(userId, couponId);
    }

    @GetMapping("/coupons/can-use")
    public CorporateCouponEligibilityResponse canUse(@RequestParam Long spaId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return redemptionService.canUseCorporateCoupon(userId, spaId);
    }

    @PostMapping("/coupons/redeem")
    public CorporateCouponResponse redeem(@Valid @RequestBody CorporateCouponRedemptionRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return redemptionService.redeemCouponForBooking(userId, request);
    }
}
