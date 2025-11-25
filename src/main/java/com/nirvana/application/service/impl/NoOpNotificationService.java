package com.nirvana.application.service.impl;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.NotificationLog;
import com.nirvana.application.model.enums.NotificationChannel;
import com.nirvana.application.model.enums.NotificationStatus;
import com.nirvana.application.repository.NotificationLogRepository;
import com.nirvana.application.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnProperty(prefix = "notifications.sendgrid", name = "enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class NoOpNotificationService implements NotificationService {

    private final NotificationLogRepository notificationLogRepository;

    @Override
    public void notifySpaOwnerBookingConfirmed(Booking booking) {
        persistLog(booking, resolveSpaOwnerDestination(booking), "Booking confirmed for your spa", NotificationStatus.SENT);
    }

    @Override
    public void notifyCustomerBookingConfirmed(Booking booking) {
        persistLog(booking, resolveCustomerDestination(booking), "Your booking is confirmed", NotificationStatus.SENT);
    }

    @Override
    public void notifyCustomerBookingCancelled(Booking booking) {
        persistLog(booking, resolveCustomerDestination(booking), "Your booking was cancelled", NotificationStatus.SENT);
    }

    @Override
    public void notifySpaOwnerBookingCancelled(Booking booking) {
        persistLog(booking, resolveSpaOwnerDestination(booking), "Booking cancelled", NotificationStatus.SENT);
    }

    private void persistLog(Booking booking, String destination, String title, NotificationStatus status) {
        if (destination == null || destination.isBlank()) {
            log.warn("Skipping notification log: destination missing for booking ref {}", booking.getBookingReference());
            return;
        }

        NotificationLog logEntry = NotificationLog.builder()
                .user(booking.getUser())
                .channel(NotificationChannel.EMAIL)
                .status(status)
                .destination(destination)
                .templateCode("BOOKING_EVENT")
                .title(title)
                .message("Booking ref " + booking.getBookingReference())
                .sentAt(booking.getUpdatedAt())
                .build();
        notificationLogRepository.save(logEntry);
        log.info("Recorded notification log for booking {} -> {}", booking.getBookingReference(), destination);
    }

    private String resolveSpaOwnerDestination(Booking booking) {
        if (booking.getSpa() != null) {
            if (booking.getSpa().getEmail() != null) {
                return booking.getSpa().getEmail();
            }
            if (booking.getSpa().getSpaManager() != null && booking.getSpa().getSpaManager().getUser() != null) {
                return booking.getSpa().getSpaManager().getUser().getEmail();
            }
        }
        return null;
    }

    private String resolveCustomerDestination(Booking booking) {
        return booking.getUser() != null ? booking.getUser().getEmail() : null;
    }
}
