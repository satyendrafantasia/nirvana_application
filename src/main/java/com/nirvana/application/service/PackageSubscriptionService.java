package com.nirvana.application.service;

import com.nirvana.application.model.dto.CreatePackagePurchaseRequest;
import com.nirvana.application.model.dto.PackageDetailsResponse;
import com.nirvana.application.model.dto.PackagePurchaseResponse;
import com.nirvana.application.model.dto.PackageUsageCheckResponse;
import com.nirvana.application.model.dto.PackageUsageConsumeRequest;
import com.nirvana.application.model.dto.PackageUsageConsumeResponse;
import com.nirvana.application.model.dto.PackageUsageHistoryResponse;

public interface PackageSubscriptionService {

    PackagePurchaseResponse purchasePackage(Long userId, CreatePackagePurchaseRequest request);

    PackageDetailsResponse getActivePackage(Long userId);

    PackageUsageCheckResponse canUsePackage(Long userId, Long spaId);

    PackageUsageConsumeResponse consumePackageSession(Long userId, PackageUsageConsumeRequest request);

    PackageUsageHistoryResponse getUsageHistory(Long userId);
}
