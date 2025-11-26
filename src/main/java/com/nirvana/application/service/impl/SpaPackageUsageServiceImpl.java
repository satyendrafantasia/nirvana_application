package com.nirvana.application.service.impl;

import com.nirvana.application.exception.NoActiveSpaPackageForUserException;
import com.nirvana.application.exception.NoRemainingSessionsException;
import com.nirvana.application.exception.SpaNotFoundException;
import com.nirvana.application.model.Booking;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.dto.SpaPackageUsageCheckResponse;
import com.nirvana.application.model.enums.spa.UserSpaPackageStatus;
import com.nirvana.application.model.spa.UserSpaPackageSubscription;
import com.nirvana.application.model.spa.UserSpaPackageUsageLog;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.repository.UserSpaPackageSubscriptionRepository;
import com.nirvana.application.repository.UserSpaPackageUsageLogRepository;
import com.nirvana.application.service.SpaPackageUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SpaPackageUsageServiceImpl implements SpaPackageUsageService {

    private final UserSpaPackageSubscriptionRepository subscriptionRepository;
    private final UserSpaPackageUsageLogRepository usageLogRepository;
    private final BookingRepository bookingRepository;
    private final SpaRepository spaRepository;

    @Override
    @Transactional(readOnly = true)
    public SpaPackageUsageCheckResponse canUseSpaPackage(Long userId, Long spaId) {
        SpaPackageUsageCheckResponse response = new SpaPackageUsageCheckResponse();
        response.setCanUse(false);
        List<UserSpaPackageStatus> activeStatuses = Collections.singletonList(UserSpaPackageStatus.ACTIVE);
        var subscriptionOpt = subscriptionRepository.findFirstByUserIdAndSpaIdAndStatusIn(userId, spaId, activeStatuses);
        if (subscriptionOpt.isEmpty()) {
            response.setReason("No active package available for this spa");
            return response;
        }
        UserSpaPackageSubscription subscription = subscriptionOpt.get();
        if (isExpired(subscription)) {
            response.setReason("Package expired");
            response.setStatus(subscription.getStatus());
            return response;
        }
        response.setSubscriptionId(subscription.getId());
        response.setLevel(subscription.getLevel());
        response.setRemainingSessions(subscription.getRemainingSessions());
        response.setStatus(subscription.getStatus());
        response.setCanUse(subscription.getRemainingSessions() > 0);
        if (!response.isCanUse()) {
            response.setReason("No remaining sessions");
        }
        return response;
    }

    @Override
    @Transactional
    public void consumeSession(Long userId, Long spaId, Long bookingId, String notes) {
        UserSpaPackageSubscription subscription = subscriptionRepository.findActiveForUpdate(userId, spaId, UserSpaPackageStatus.ACTIVE)
                .orElseThrow(() -> new NoActiveSpaPackageForUserException("No active spa package for this spa"));
        if (isExpired(subscription)) {
            throw new NoActiveSpaPackageForUserException("Spa package expired");
        }
        if (subscription.getRemainingSessions() <= 0) {
            subscription.setStatus(UserSpaPackageStatus.EXHAUSTED);
            subscriptionRepository.save(subscription);
            throw new NoRemainingSessionsException("No remaining sessions");
        }

        Spa spa = spaRepository.findById(spaId).orElseThrow(() -> new SpaNotFoundException("Spa not found: " + spaId));
        Booking booking = null;
        if (bookingId != null) {
            booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        }

        int sessionNumber = subscription.getTotalSessions() - subscription.getRemainingSessions() + 1;
        subscription.setRemainingSessions(subscription.getRemainingSessions() - 1);
        if (subscription.getRemainingSessions() <= 0) {
            subscription.setStatus(UserSpaPackageStatus.EXHAUSTED);
        }
        subscriptionRepository.save(subscription);

        UserSpaPackageUsageLog usageLog = UserSpaPackageUsageLog.builder()
                .subscription(subscription)
                .user(subscription.getUser())
                .spa(spa)
                .booking(booking)
                .usageDate(OffsetDateTime.now())
                .sessionNumber(sessionNumber)
                .notes(notes)
                .build();
        usageLogRepository.save(usageLog);
    }

    private boolean isExpired(UserSpaPackageSubscription subscription) {
        if (subscription.getExpiryDate() != null && subscription.getExpiryDate().isBefore(OffsetDateTime.now())) {
            subscription.setStatus(UserSpaPackageStatus.EXPIRED);
            subscriptionRepository.save(subscription);
            return true;
        }
        return false;
    }
}
