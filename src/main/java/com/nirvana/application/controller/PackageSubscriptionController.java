package com.nirvana.application.controller;

import com.nirvana.application.model.dto.CreatePackagePurchaseRequest;
import com.nirvana.application.model.dto.PackageDetailsResponse;
import com.nirvana.application.model.dto.PackagePurchaseResponse;
import com.nirvana.application.model.dto.PackageUsageCheckResponse;
import com.nirvana.application.model.dto.PackageUsageConsumeRequest;
import com.nirvana.application.model.dto.PackageUsageConsumeResponse;
import com.nirvana.application.model.dto.PackageUsageHistoryResponse;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.PackageSubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/packages")
@RequiredArgsConstructor
@Validated
public class PackageSubscriptionController {

    private final PackageSubscriptionService packageSubscriptionService;

    @PostMapping("/purchase")
    public PackagePurchaseResponse purchasePackage(@Valid @RequestBody CreatePackagePurchaseRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return packageSubscriptionService.purchasePackage(userId, request);
    }

    @GetMapping("/active")
    public PackageDetailsResponse getActivePackage() {
        Long userId = SecurityUtils.getCurrentUserId();
        return packageSubscriptionService.getActivePackage(userId);
    }

    @GetMapping("/can-use")
    public PackageUsageCheckResponse canUsePackage(@RequestParam("spaId") Long spaId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return packageSubscriptionService.canUsePackage(userId, spaId);
    }

    @PostMapping("/use")
    public PackageUsageConsumeResponse usePackage(@Valid @RequestBody PackageUsageConsumeRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return packageSubscriptionService.consumePackageSession(userId, request);
    }

    @GetMapping("/usage-history")
    public PackageUsageHistoryResponse usageHistory() {
        Long userId = SecurityUtils.getCurrentUserId();
        return packageSubscriptionService.getUsageHistory(userId);
    }
}
