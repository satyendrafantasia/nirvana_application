package com.nirvana.application.service.impl;

import com.nirvana.application.model.Booking;
import com.nirvana.application.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnMissingBean(NotificationService.class)
public class NoOpNotificationService implements NotificationService {
    @Override
    public void notifySpaOwnerBookingConfirmed(Booking booking) {
        log.debug("[notification] spa owner booking confirmed id={} ref={}", booking.getId(), booking.getBookingReference());
    }

    @Override
    public void notifyCustomerBookingConfirmed(Booking booking) {
        log.debug("[notification] customer booking confirmed id={} ref={}", booking.getId(), booking.getBookingReference());
    }

    @Override
    public void notifyCustomerBookingCancelled(Booking booking) {
        log.debug("[notification] customer booking cancelled id={} ref={}", booking.getId(), booking.getBookingReference());
    }

    @Override
    public void notifySpaOwnerBookingCancelled(Booking booking) {
        log.debug("[notification] spa owner booking cancelled id={} ref={}", booking.getId(), booking.getBookingReference());
    }
}
