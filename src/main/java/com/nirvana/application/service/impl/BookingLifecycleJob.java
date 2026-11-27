package com.nirvana.application.service.impl;

import com.nirvana.application.config.RazorpayProperties;
import com.nirvana.application.model.Payment;
import com.nirvana.application.model.enums.PaymentStatus;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Periodic housekeeping for bookings. Every step re-checks the booking under a row lock, so the
 * job is safe to run on several instances at once.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingLifecycleJob {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final BookingLifecycleService lifecycle;
    private final PaymentService paymentService;
    private final RazorpayProperties razorpayProperties;

    @Value("${reliability.bookings.pending-payment-expiry-minutes:30}")
    private int pendingPaymentExpiryMinutes;

    @Value("${reliability.bookings.auto-complete-after-hours:24}")
    private int autoCompleteAfterHours;

    @Value("${reliability.bookings.reminder-lead-hours:24}")
    private int reminderLeadHours;

    @Scheduled(initialDelayString = "${reliability.bookings.expiry-interval-ms:60000}",
            fixedDelayString = "${reliability.bookings.expiry-interval-ms:60000}")
    public void expireUnpaidBookings() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (Long bookingId : bookingRepository.findExpirablePendingPaymentIds(
                now.minusMinutes(pendingPaymentExpiryMinutes), now)) {
            try {
                // Ask the gateway first: a payment may have been captured without the browser
                // or webhook telling us. If the gateway cannot be reached, leave the booking alone.
                if (!gatewayConfirmsUnpaid(bookingId)) {
                    continue;
                }
                lifecycle.expireUnpaid(bookingId);
            } catch (RuntimeException ex) {
                log.warn("Could not expire unpaid booking {}: {}", bookingId, ex.getMessage());
            }
        }
    }

    @Scheduled(initialDelayString = "${reliability.bookings.auto-complete-interval-ms:900000}",
            fixedDelayString = "${reliability.bookings.auto-complete-interval-ms:900000}")
    public void completePastAppointments() {
        if (autoCompleteAfterHours <= 0) {
            return; // disabled: the spa closes out every appointment itself
        }
        OffsetDateTime endedBefore = OffsetDateTime.now(ZoneOffset.UTC).minusHours(autoCompleteAfterHours);
        for (Long bookingId : bookingRepository.findAutoCompletableIds(endedBefore)) {
            try {
                lifecycle.autoComplete(bookingId, endedBefore);
            } catch (RuntimeException ex) {
                log.warn("Could not auto-complete booking {}: {}", bookingId, ex.getMessage());
            }
        }
    }

    @Scheduled(initialDelayString = "${reliability.bookings.reminder-interval-ms:300000}",
            fixedDelayString = "${reliability.bookings.reminder-interval-ms:300000}")
    public void remindUpcomingAppointments() {
        if (reminderLeadHours <= 0) {
            return; // reminders are switched off
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (Long bookingId : bookingRepository.findReminderDueIds(now, now.plusHours(reminderLeadHours))) {
            try {
                lifecycle.sendReminder(bookingId, reminderLeadHours);
            } catch (RuntimeException ex) {
                log.warn("Could not send reminder for booking {}: {}", bookingId, ex.getMessage());
            }
        }
    }

    private boolean gatewayConfirmsUnpaid(Long bookingId) {
        for (Payment payment : paymentRepository.findByBookingId(bookingId)) {
            boolean open = payment.getPaymentStatus() == PaymentStatus.INIT || payment.getPaymentStatus() == PaymentStatus.PENDING;
            if (!open || !"RAZORPAY".equals(payment.getGateway()) || payment.getIntentId() == null) {
                continue;
            }
            if (!razorpayProperties.credentialsConfigured()) return false;
            try {
                paymentService.reconcilePendingOrder(payment.getIntentId());
            } catch (RuntimeException ex) {
                log.warn("Gateway check failed for booking {}; keeping it pending: {}", bookingId, ex.getMessage());
                return false;
            }
        }
        return true;
    }
}
