package com.nirvana.application.service.impl;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.service.NotificationSchedulingService;
import com.nirvana.application.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;


@Service
@org.springframework.transaction.annotation.Transactional
@RequiredArgsConstructor
@Slf4j
public class NotificationSchedulingServiceImpl implements NotificationSchedulingService {

    enum Event { CONFIRMED, CANCELLED, RESCHEDULED }

    private final NotificationService notificationService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final BookingRepository bookingRepository;
    private final PlatformTransactionManager transactionManager;
    private final com.nirvana.application.service.SpaBookingAlerts spaAlerts;

    @Override public void scheduleBookingNotifications(Booking booking) { enqueue(booking, Event.CONFIRMED); }
    @Override public void bookingCancelled(Booking booking) { enqueue(booking, Event.CANCELLED); }
    @Override public void bookingRescheduled(Booking booking) { enqueue(booking, Event.RESCHEDULED); }

    enum Audience { CUSTOMER_EMAIL, SPA_EMAIL, SPA_MOBILE }

    private void enqueue(Booking booking, Event event) {
        int revision = booking.getRescheduleCount() == null ? 0 : booking.getRescheduleCount();
        for (Audience audience : Audience.values()) {
            if (event == Event.CANCELLED && audience != Audience.CUSTOMER_EMAIL &&
                    BookingLifecycleService.PAYMENT_TIMEOUT.equals(booking.getCancellationReasonCode())) continue;
            jdbc.update("""
                    INSERT INTO booking_alert_job (booking_id,event_kind,audience,revision)
                    VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE id=id
                    """, booking.getId(), event.name(), audience.name(), revision);
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${booking.alerts.poll-ms:5000}")
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void drain() {
        var due = jdbc.queryForList("""
                SELECT id FROM booking_alert_job WHERE state='PENDING' AND next_attempt<=CURRENT_TIMESTAMP
                ORDER BY next_attempt,id LIMIT 60
                """, Long.class);
        var tx = new TransactionTemplate(transactionManager);
        for (long id : due) {
            try {
                tx.executeWithoutResult(status -> process(id));
            } catch (RuntimeException failure) {
                // The delivery transaction rolled back, including any new spa confirmation token.
                jdbc.update("""
                        UPDATE booking_alert_job SET state=CASE WHEN attempts>=5 THEN 'FAILED' ELSE 'PENDING' END,
                        next_attempt=TIMESTAMPADD(SECOND,30 * POWER(2,attempts),CURRENT_TIMESTAMP), attempts=attempts+1
                        WHERE id=? AND state='PENDING' AND next_attempt<=CURRENT_TIMESTAMP
                        """, id);
                log.warn("Booking alert job {} failed ({})", id, failure.getClass().getSimpleName());
            }
        }
    }

    private void process(long id) {
        var rows = jdbc.queryForList("""
                SELECT booking_id,event_kind,audience,revision FROM booking_alert_job
                WHERE id=? AND state='PENDING' AND next_attempt<=CURRENT_TIMESTAMP FOR UPDATE
                """, id);
        if (rows.isEmpty()) return;
        var row = rows.get(0);
        var booking = bookingRepository.findById(((Number)row.get("booking_id")).longValue()).orElse(null);
        var event = Event.valueOf((String)row.get("event_kind"));
        var audience = Audience.valueOf((String)row.get("audience"));
        boolean current = booking != null && switch (event) {
            case CANCELLED -> booking.getStatus() == BookingStatus.CANCELLED;
            case CONFIRMED -> (booking.getStatus() == BookingStatus.CONFIRMED || booking.getStatus() == BookingStatus.ARRIVED)
                    && sameRevision(booking, row);
            case RESCHEDULED -> (booking.getStatus() == BookingStatus.CONFIRMED || booking.getStatus() == BookingStatus.PENDING_PAYMENT)
                    && sameRevision(booking, row);
        };
        String state = "OBSOLETE";
        if (current) {
            try {
                dispatch(booking, event, audience);
                state = "DONE";
                if (audience == Audience.SPA_MOBILE && event != Event.CANCELLED
                        && com.nirvana.application.service.SpaAlertSms.mobile(booking.getSpa().getPhone()).isEmpty())
                    state = "NEEDS_CONTACT";
            } catch (com.nirvana.application.service.BookingContactUnavailable unavailable) {
                state = "NEEDS_CONTACT";
            }
        }
        jdbc.update("UPDATE booking_alert_job SET state=? WHERE id=?", state, id);
    }

    private boolean sameRevision(Booking booking, java.util.Map<String,Object> row) {
        return ((Number)row.get("revision")).intValue() == (booking.getRescheduleCount() == null ? 0 : booking.getRescheduleCount());
    }

    private void dispatch(Booking booking, Event event, Audience audience) {
        switch (audience) {
            case CUSTOMER_EMAIL -> {
                switch (event) {
                    case CONFIRMED -> notificationService.notifyCustomerBookingConfirmed(booking);
                    case RESCHEDULED -> notificationService.notifyCustomerBookingRescheduled(booking);
                    case CANCELLED -> notificationService.notifyCustomerBookingCancelled(booking);
                }
            }
            case SPA_EMAIL -> {
                switch (event) {
                    case CONFIRMED -> notificationService.notifySpaOwnerBookingConfirmed(booking);
                    case RESCHEDULED -> notificationService.notifySpaOwnerBookingRescheduled(booking);
                    case CANCELLED -> notificationService.notifySpaOwnerBookingCancelled(booking);
                }
            }
            case SPA_MOBILE -> {
                switch (event) {
                    case CONFIRMED -> spaAlerts.bookingConfirmed(booking);
                    case RESCHEDULED -> { if (booking.getStatus() == BookingStatus.CONFIRMED) spaAlerts.bookingRescheduled(booking); }
                    case CANCELLED -> spaAlerts.bookingCancelled(booking);
                }
            }
        }
    }
}
