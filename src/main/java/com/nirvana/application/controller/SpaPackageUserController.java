package com.nirvana.application.controller;

import com.nirvana.application.exception.SpaNotFoundException;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.dto.BuySpaPackageRequest;
import com.nirvana.application.model.dto.BuySpaPackageResponse;
import com.nirvana.application.model.dto.SpaPackageListingResponse;
import com.nirvana.application.model.dto.SpaPackageResponse;
import com.nirvana.application.model.dto.SpaPackageUsageCheckResponse;
import com.nirvana.application.model.dto.UserSpaPackageSummaryResponse;
import com.nirvana.application.model.dto.UserSpaPackageUsageHistoryResponse;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.service.SpaPackageService;
import com.nirvana.application.service.UserSpaPackageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class SpaPackageUserController {

    private final SpaPackageService spaPackageService;
    private final UserSpaPackageService userSpaPackageService;
    private final SpaRepository spaRepository;

    @GetMapping("/spa/{spaId}/packages/available")
    public SpaPackageListingResponse listAvailablePackages(@PathVariable("spaId") Long spaId) {
        List<SpaPackageResponse> packages = spaPackageService.getSpaPackages(spaId);
        Spa spa = spaRepository.findById(spaId).orElseThrow(() -> new SpaNotFoundException("Spa not found: " + spaId));
        String spaName = spa.getName();
        return new SpaPackageListingResponse(spaId, spaName, packages);
    }

    @PostMapping("/spa/{spaId}/packages/{spaPackageId}/buy")
    public BuySpaPackageResponse buySpaPackage(@PathVariable("spaId") Long spaId,
                                               @PathVariable("spaPackageId") Long spaPackageId,
                                               @Valid @RequestBody BuySpaPackageRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        request.setSpaId(spaId);
        request.setSpaPackageId(spaPackageId);
        return userSpaPackageService.buySpaPackage(userId, request);
    }

    @GetMapping("/user/packages")
    public List<UserSpaPackageSummaryResponse> userPackages() {
        Long userId = SecurityUtils.getCurrentUserId();
        return userSpaPackageService.getUserSpaPackages(userId);
    }

    @GetMapping("/user/packages/{subscriptionId}/usage")
    public UserSpaPackageUsageHistoryResponse usageHistory(@PathVariable("subscriptionId") Long subscriptionId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return userSpaPackageService.getUserSpaPackageUsageHistory(userId, subscriptionId);
    }

    @GetMapping("/user/packages/can-use")
    public SpaPackageUsageCheckResponse canUse(@RequestParam("spaId") Long spaId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return userSpaPackageService.canUseSpaPackage(userId, spaId);
    }
}
