package com.nirvana.application.service.impl;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.UserPackageSubscription;
import com.nirvana.application.model.UserPackageUsageLog;
import com.nirvana.application.model.enums.PackageStatus;
import com.nirvana.application.model.enums.spa.UserSpaPackageStatus;
import com.nirvana.application.model.spa.UserSpaPackageSubscription;
import com.nirvana.application.model.spa.UserSpaPackageUsageLog;
import com.nirvana.application.repository.UserPackageSubscriptionRepository;
import com.nirvana.application.repository.UserPackageUsageLogRepository;
import com.nirvana.application.repository.UserSpaPackageSubscriptionRepository;
import com.nirvana.application.repository.UserSpaPackageUsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Gives back the package session a cancelled booking had used. The usage record for the booking
 * is removed, so calling this twice for the same booking returns the session only once.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PackageSessionRestorer {

    private final UserPackageUsageLogRepository centralUsage;
    private final UserPackageSubscriptionRepository centralSubscriptions;
    private final UserSpaPackageUsageLogRepository spaUsage;
    private final UserSpaPackageSubscriptionRepository spaSubscriptions;

    /** Returns the number of sessions given back (0 when the booking was not paid by a package). */
    @Transactional(propagation = Propagation.MANDATORY)
    public int restoreFor(Booking booking) {
        int restored = 0;
        for (UserPackageUsageLog usage : centralUsage.findByBooking_Id(booking.getId())) {
            restoreCentral(usage);
            restored++;
        }
        for (UserSpaPackageUsageLog usage : spaUsage.findByBooking_Id(booking.getId())) {
            restoreSpa(usage);
            restored++;
        }
        if (restored > 0) {
            log.info("Returned {} package session(s) for cancelled booking {}", restored, booking.getId());
        }
        return restored;
    }

    private void restoreCentral(UserPackageUsageLog usage) {
        UserPackageSubscription used = centralSubscriptions.findForUpdate(usage.getSubscription().getId()).orElseThrow();
        UserPackageSubscription target = used;
        if (used.getStatus() == PackageStatus.EXHAUSTED && !expired(used.getExpiryDate())) {
            // Only one subscription may be active per customer. If they bought another since this
            // one ran out, the session goes to that one so it stays usable.
            Long userId = used.getUser().getId();
            UserPackageSubscription active = centralSubscriptions.findByUserIdOrderByPurchaseDateDesc(userId).stream()
                    .filter(s -> s.getStatus() == PackageStatus.ACTIVE && !s.getId().equals(used.getId()))
                    .findFirst().orElse(null);
            if (active != null) {
                target = centralSubscriptions.findForUpdate(active.getId()).orElseThrow();
            } else {
                used.setStatus(PackageStatus.ACTIVE);
            }
        }
        target.setRemainingSessions(target.getRemainingSessions() + 1);
        centralSubscriptions.save(target);
        centralUsage.delete(usage);
    }

    private void restoreSpa(UserSpaPackageUsageLog usage) {
        UserSpaPackageSubscription used = spaSubscriptions.findForUpdate(usage.getSubscription().getId()).orElseThrow();
        UserSpaPackageSubscription target = used;
        if (used.getStatus() == UserSpaPackageStatus.EXHAUSTED && !expired(used.getExpiryDate())) {
            // One active package per customer per spa: same rule as above.
            UserSpaPackageSubscription active = spaSubscriptions
                    .findFirstByUserIdAndSpaIdAndStatus(used.getUser().getId(), used.getSpa().getId(), UserSpaPackageStatus.ACTIVE)
                    .filter(s -> !s.getId().equals(used.getId())).orElse(null);
            if (active != null) {
                target = spaSubscriptions.findForUpdate(active.getId()).orElseThrow();
            } else {
                used.setStatus(UserSpaPackageStatus.ACTIVE);
            }
        }
        target.setRemainingSessions(target.getRemainingSessions() + 1);
        spaSubscriptions.save(target);
        spaUsage.delete(usage);
    }

    private boolean expired(OffsetDateTime expiry) {
        return expiry != null && expiry.isBefore(OffsetDateTime.now());
    }
}
