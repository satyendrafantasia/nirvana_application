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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Package", description = "Spa-specific package discovery and usage for end users")
@SecurityRequirement(name = "bearerAuth")
public class SpaPackageUserController {

    private final SpaPackageService spaPackageService;
    private final UserSpaPackageService userSpaPackageService;
    private final SpaRepository spaRepository;

    @GetMapping("/spa/{spaId}/packages/available")
    @Operation(summary = "List spa packages", description = "List packages available for a spa including remaining sessions and eligibility.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Packages returned", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SpaPackageListingResponse.class)))
    })
    public SpaPackageListingResponse listAvailablePackages(@Parameter(description = "Spa identifier") @PathVariable("spaId") Long spaId) {
        List<SpaPackageResponse> packages = spaPackageService.getSpaPackages(spaId);
        Spa spa = spaRepository.findById(spaId).orElseThrow(() -> new SpaNotFoundException("Spa not found: " + spaId));
        String spaName = spa.getName();
        return new SpaPackageListingResponse(spaId, spaName, packages);
    }

    @PostMapping("/spa/{spaId}/packages/{spaPackageId}/buy")
    @Operation(summary = "Buy spa package", description = "Purchase a spa package with wallet or payment gateway and link to the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Package purchased", content = @Content(mediaType = "application/json", schema = @Schema(implementation = BuySpaPackageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid purchase", content = @Content(schema = @Schema(implementation = com.nirvana.application.api.ApiErrorResponse.class)))
    })
    public BuySpaPackageResponse buySpaPackage(@Parameter(description = "Spa identifier") @PathVariable("spaId") Long spaId,
                                               @Parameter(description = "Package identifier") @PathVariable("spaPackageId") Long spaPackageId,
                                               @Valid @RequestBody BuySpaPackageRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        request.setSpaId(spaId);
        request.setSpaPackageId(spaPackageId);
        return userSpaPackageService.buySpaPackage(userId, request);
    }

    @GetMapping("/user/packages")
    @Operation(summary = "List my spa packages", description = "List packages purchased by the authenticated user across spas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Packages returned", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = UserSpaPackageSummaryResponse.class))))
    })
    public List<UserSpaPackageSummaryResponse> userPackages() {
        Long userId = SecurityUtils.getCurrentUserId();
        return userSpaPackageService.getUserSpaPackages(userId);
    }

    @GetMapping("/user/packages/{subscriptionId}/usage")
    @Operation(summary = "Get package usage", description = "Show how a purchased package has been consumed across bookings.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usage history", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserSpaPackageUsageHistoryResponse.class)))
    })
    public UserSpaPackageUsageHistoryResponse usageHistory(@Parameter(description = "Subscription identifier") @PathVariable("subscriptionId") Long subscriptionId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return userSpaPackageService.getUserSpaPackageUsageHistory(userId, subscriptionId);
    }

    @GetMapping("/user/packages/can-use")
    @Operation(summary = "Check package applicability", description = "Check if the user can apply an active package to the requested spa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Applicability determined", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SpaPackageUsageCheckResponse.class)))
    })
    public SpaPackageUsageCheckResponse canUse(@Parameter(description = "Spa identifier") @RequestParam("spaId") Long spaId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return userSpaPackageService.canUseSpaPackage(userId, spaId);
    }
}
