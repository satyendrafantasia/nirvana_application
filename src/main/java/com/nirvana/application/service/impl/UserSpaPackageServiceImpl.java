package com.nirvana.application.service.impl;

import com.nirvana.application.exception.PaymentFailedException;
import com.nirvana.application.exception.SpaNotFoundException;
import com.nirvana.application.exception.SpaPackageInactiveException;
import com.nirvana.application.exception.SpaPackageNotFoundException;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.User;
import com.nirvana.application.model.dto.BuySpaPackageRequest;
import com.nirvana.application.model.dto.BuySpaPackageResponse;
import com.nirvana.application.model.dto.SpaPackageUsageCheckResponse;
import com.nirvana.application.model.dto.UserSpaPackageSummaryResponse;
import com.nirvana.application.model.dto.UserSpaPackageUsageHistoryResponse;
import com.nirvana.application.model.dto.SpaPackageUsageItemResponse;
import com.nirvana.application.model.enums.spa.PaymentStatus;
import com.nirvana.application.model.enums.spa.SpaPackageStatus;
import com.nirvana.application.model.enums.spa.UserSpaPackageStatus;
import com.nirvana.application.model.spa.SpaPackage;
import com.nirvana.application.model.spa.UserSpaPackageSubscription;
import com.nirvana.application.repository.SpaPackageRepository;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.repository.UserRepository;
import com.nirvana.application.repository.UserSpaPackageSubscriptionRepository;
import com.nirvana.application.repository.UserSpaPackageUsageLogRepository;
import com.nirvana.application.service.SpaPackageNotificationService;
import com.nirvana.application.service.UserSpaPackageService;
import com.nirvana.application.service.payment.PaymentClient;
import com.nirvana.application.service.payment.PaymentResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserSpaPackageServiceImpl implements UserSpaPackageService {

    private final SpaRepository spaRepository;
    private final UserRepository userRepository;
    private final SpaPackageRepository spaPackageRepository;
    private final UserSpaPackageSubscriptionRepository subscriptionRepository;
    private final UserSpaPackageUsageLogRepository usageLogRepository;
    private final PaymentClient paymentClient;
    private final SpaPackageNotificationService notificationService;

    @Override
    @Transactional
    public BuySpaPackageResponse buySpaPackage(Long userId, BuySpaPackageRequest request) {
        Spa spa = spaRepository.findById(request.getSpaId())
                .orElseThrow(() -> new SpaNotFoundException("Spa not found: " + request.getSpaId()));
        SpaPackage spaPackage = spaPackageRepository.findBySpaIdAndId(spa.getId(), request.getSpaPackageId())
                .orElseThrow(() -> new SpaPackageNotFoundException("Spa package not found for spa"));
        if (spaPackage.getStatus() != SpaPackageStatus.ACTIVE) {
            throw new SpaPackageInactiveException("Spa package is inactive");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new SpaPackageNotFoundException("User not found: " + userId));

        OffsetDateTime purchaseDate = OffsetDateTime.now();
        UserSpaPackageSubscription subscription = UserSpaPackageSubscription.builder()
                .user(user)
                .spa(spa)
                .spaPackage(spaPackage)
                .level(spaPackage.getLevel())
                .pricePaid(spaPackage.getPrice())
                .totalSessions(spaPackage.getFreeSessionsCount())
                .remainingSessions(spaPackage.getFreeSessionsCount())
                .status(UserSpaPackageStatus.CANCELLED)
                .purchaseDate(purchaseDate)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentReferenceId(request.getPaymentReference())
                .build();
        subscriptionRepository.save(subscription);

        PaymentResult paymentResult = paymentClient.processPayment(userId, spa.getId(), spaPackage.getPrice(), request.getPaymentMethod(), request.getPaymentReference());
        subscription.setPaymentStatus(paymentResult.getStatus());
        subscription.setPaymentReferenceId(paymentResult.getReferenceId());

        if (paymentResult.getStatus() == PaymentStatus.SUCCESS) {
            subscription.setStatus(UserSpaPackageStatus.ACTIVE);
            subscription.setRemainingSessions(spaPackage.getFreeSessionsCount());
            subscriptionRepository.save(subscription);
            notificationService.notifySpaOwnerOfPackagePurchase(spa.getId(), userId, subscription.getId(), spaPackage.getLevel());
            return toBuyResponse(subscription, spa.getName());
        }

        subscriptionRepository.save(subscription);
        throw new PaymentFailedException("Payment failed for spa package purchase");
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

    private BuySpaPackageResponse toBuyResponse(UserSpaPackageSubscription subscription, String spaName) {
        BuySpaPackageResponse response = new BuySpaPackageResponse();
        response.setSubscriptionId(subscription.getId());
        response.setSpaId(subscription.getSpa().getId());
        response.setSpaName(spaName);
        response.setLevel(subscription.getLevel());
        response.setPricePaid(subscription.getPricePaid());
        response.setTotalSessions(subscription.getTotalSessions());
        response.setRemainingSessions(subscription.getRemainingSessions());
        response.setStatus(subscription.getStatus());
        response.setPaymentStatus(subscription.getPaymentStatus());
        return response;
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
