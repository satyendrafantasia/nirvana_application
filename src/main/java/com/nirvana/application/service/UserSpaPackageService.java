package com.nirvana.application.service;

import com.nirvana.application.model.dto.BuySpaPackageRequest;
import com.nirvana.application.model.dto.BuySpaPackageResponse;
import com.nirvana.application.model.dto.SpaPackageUsageCheckResponse;
import com.nirvana.application.model.dto.UserSpaPackageSummaryResponse;
import com.nirvana.application.model.dto.UserSpaPackageUsageHistoryResponse;

import java.util.List;

public interface UserSpaPackageService {

    BuySpaPackageResponse buySpaPackage(Long userId, BuySpaPackageRequest request);

    List<UserSpaPackageSummaryResponse> getUserSpaPackages(Long userId);

    UserSpaPackageUsageHistoryResponse getUserSpaPackageUsageHistory(Long userId, Long subscriptionId);

    SpaPackageUsageCheckResponse canUseSpaPackage(Long userId, Long spaId);
}
