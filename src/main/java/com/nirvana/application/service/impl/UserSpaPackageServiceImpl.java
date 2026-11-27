package com.nirvana.application.service.impl;

import com.nirvana.application.exception.SpaPackageNotFoundException;
import com.nirvana.application.model.dto.BuySpaPackageRequest;
import com.nirvana.application.model.dto.BuySpaPackageResponse;
import com.nirvana.application.model.dto.SpaPackageUsageCheckResponse;
import com.nirvana.application.model.dto.UserSpaPackageSummaryResponse;
import com.nirvana.application.model.dto.UserSpaPackageUsageHistoryResponse;
import com.nirvana.application.model.dto.SpaPackageUsageItemResponse;
import com.nirvana.application.model.enums.spa.UserSpaPackageStatus;
import com.nirvana.application.model.spa.UserSpaPackageSubscription;
import com.nirvana.application.repository.UserSpaPackageSubscriptionRepository;
import com.nirvana.application.repository.UserSpaPackageUsageLogRepository;
import com.nirvana.application.service.UserSpaPackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserSpaPackageServiceImpl implements UserSpaPackageService {

    private final UserSpaPackageSubscriptionRepository subscriptionRepository;
    private final UserSpaPackageUsageLogRepository usageLogRepository;

    @Override
    @Transactional
    public BuySpaPackageResponse buySpaPackage(Long userId, BuySpaPackageRequest request) {
        throw new IllegalStateException("Use spa package Razorpay checkout to purchase and verify payment");
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSpaPackageSummaryResponse> getUserSpaPackages(Long userId) {
        return subscriptionRepository.findByUserIdOrderByPurchaseDateDesc(userId).stream()
                .map(subscription -> toSummary(subscription, subscription.getSpa().getName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserSpaPackageUsageHistoryResponse getUserSpaPackageUsageHistory(Long userId, Long subscriptionId) {
        UserSpaPackageSubscription subscription = subscriptionRepository.findByIdAndUserId(subscriptionId, userId)
                .orElseThrow(() -> new SpaPackageNotFoundException("Subscription not found"));
        List<SpaPackageUsageItemResponse> usages = usageLogRepository.findBySubscriptionIdOrderByUsageDateDesc(subscriptionId)
                .stream()
                .map(log -> {
                    SpaPackageUsageItemResponse dto = new SpaPackageUsageItemResponse();
                    dto.setBookingId(log.getBooking() != null ? log.getBooking().getId() : null);
                    dto.setUsageDate(log.getUsageDate());
                    dto.setSessionNumber(log.getSessionNumber());
                    return dto;
                })
                .toList();
        return new UserSpaPackageUsageHistoryResponse(subscription.getId(), subscription.getSpa().getId(), subscription.getSpa().getName(), usages);
    }

    @Override
    @Transactional(readOnly = true)
    public SpaPackageUsageCheckResponse canUseSpaPackage(Long userId, Long spaId) {
        SpaPackageUsageCheckResponse response = new SpaPackageUsageCheckResponse();
        response.setCanUse(false);
        List<UserSpaPackageStatus> eligibleStatuses = Collections.singletonList(UserSpaPackageStatus.ACTIVE);
        var subscriptionOpt = subscriptionRepository.findFirstByUserIdAndSpaIdAndStatusIn(userId, spaId, eligibleStatuses);
        if (subscriptionOpt.isEmpty()) {
            response.setReason("No active package for this spa");
            return response;
        }
        UserSpaPackageSubscription subscription = subscriptionOpt.get();
        if (isExpired(subscription)) {
            response.setReason("Package expired");
            response.setStatus(subscription.getStatus());
            return response;
        }
        response.setCanUse(subscription.getRemainingSessions() > 0);
        response.setSubscriptionId(subscription.getId());
        response.setLevel(subscription.getLevel());
        response.setRemainingSessions(subscription.getRemainingSessions());
        response.setStatus(subscription.getStatus());
        if (subscription.getRemainingSessions() <= 0) {
            response.setCanUse(false);
            response.setReason("No remaining sessions");
        }
        return response;
    }

    private boolean isExpired(UserSpaPackageSubscription subscription) {
        if (subscription.getExpiryDate() != null && subscription.getExpiryDate().isBefore(OffsetDateTime.now())) {
            subscription.setStatus(UserSpaPackageStatus.EXPIRED);
            subscriptionRepository.save(subscription);
            return true;
        }
        return false;
    }

    private UserSpaPackageSummaryResponse toSummary(UserSpaPackageSubscription subscription, String spaName) {
        UserSpaPackageSummaryResponse response = new UserSpaPackageSummaryResponse();
        response.setSubscriptionId(subscription.getId());
        response.setSpaId(subscription.getSpa().getId());
        response.setSpaName(spaName);
        response.setLevel(subscription.getLevel());
        response.setPricePaid(subscription.getPricePaid());
        response.setTotalSessions(subscription.getTotalSessions());
        response.setRemainingSessions(subscription.getRemainingSessions());
        response.setStatus(subscription.getStatus());
        response.setPurchaseDate(subscription.getPurchaseDate());
        response.setExpiryDate(subscription.getExpiryDate());
        return response;
    }
}
