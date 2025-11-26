package com.nirvana.application.service.impl;

import com.nirvana.application.exception.SpaNotFoundException;
import com.nirvana.application.model.Notification;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.User;
import com.nirvana.application.model.enums.spa.SpaPackageLevel;
import com.nirvana.application.repository.NotificationRepository;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.repository.UserRepository;
import com.nirvana.application.service.SpaPackageNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpaPackageNotificationServiceImpl implements SpaPackageNotificationService {

    private final NotificationRepository notificationRepository;
    private final SpaRepository spaRepository;
    private final UserRepository userRepository;

    @Override
    public void notifySpaOwnerOfPackagePurchase(Long spaId, Long userId, Long subscriptionId, SpaPackageLevel level) {
        Spa spa = spaRepository.findById(spaId)
                .orElseThrow(() -> new SpaNotFoundException("Spa not found: " + spaId));
        User purchaser = userRepository.findById(userId).orElse(null);
        User recipient = spa.getSpaManager() != null ? spa.getSpaManager().getUser() : null;
        if (recipient == null) {
            log.warn("Spa {} has no manager/owner user to notify", spaId);
            return;
        }

        String purchaserName = purchaser != null ? purchaser.getName() : "Unknown user";
        String title = "Package purchased";
        String message = String.format("%s purchased %s package at %s (subscription #%d)",
                purchaserName,
                level,
                spa.getName(),
                subscriptionId);

        Notification notification = Notification.builder()
                .recipient(recipient)
                .type("PACKAGE_PURCHASE")
                .title(title)
                .message(message)
                .build();
        notificationRepository.save(notification);
        log.info("Notified spa owner {} for package purchase {}", recipient.getId(), subscriptionId);
    }
}
