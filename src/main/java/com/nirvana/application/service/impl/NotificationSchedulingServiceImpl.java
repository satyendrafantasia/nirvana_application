package com.nirvana.application.service.impl;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.service.NotificationSchedulingService;
import com.nirvana.application.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationSchedulingServiceImpl implements NotificationSchedulingService {

    enum Event { CONFIRMED, CANCELLED, RESCHEDULED }

    private final NotificationService notificationService;
    private final TaskScheduler taskScheduler;
    private final BookingRepository bookingRepository;
    private final PlatformTransactionManager transactionManager;

    @Override
    public void scheduleBookingNotifications(Booking booking) {
        afterCommit(booking, Event.CONFIRMED);
    }

    @Override
    public void bookingCancelled(Booking booking) {
        afterCommit(booking, Event.CANCELLED);
    }

    @Override
    public void bookingRescheduled(Booking booking) {
        afterCommit(booking, Event.RESCHEDULED);
    }

    /**
     * Email is slow and can fail, so it never runs inside the booking transaction (which holds
     * schedule locks) and never for a change that ends up rolled back.
     */
    private void afterCommit(Booking booking, Event event) {
        Long bookingId = booking.getId();
        if (bookingId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(bookingId, event);
                }
            });
        } else {
            submit(bookingId, event);
        }
    }

    private void submit(Long bookingId, Event event) {
        try {
            taskScheduler.schedule(() -> deliver(bookingId, event), Instant.now());
        } catch (RuntimeException ex) {
            log.warn("Could not queue {} notification for booking {}: {}", event, bookingId, ex.getMessage());
        }
    }

    /** Loads the committed booking and sends both emails. A failure never reaches the caller. */
    void deliver(Long bookingId, Event event) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                Booking booking = bookingRepository.findById(bookingId).orElse(null);
                if (booking == null || !stillApplies(booking, event)) {
                    return;
                }
                switch (event) {
                    case CONFIRMED -> {
                        attempt(booking, notificationService::notifyCustomerBookingConfirmed);
                        attempt(booking, notificationService::notifySpaOwnerBookingConfirmed);
                    }
                    case CANCELLED -> {
                        attempt(booking, notificationService::notifyCustomerBookingCancelled);
                        // The spa was never told about a booking that expired unpaid.
                        if (!BookingLifecycleService.PAYMENT_TIMEOUT.equals(booking.getCancellationReasonCode())) {
                            attempt(booking, notificationService::notifySpaOwnerBookingCancelled);
                        }
                    }
                    case RESCHEDULED -> {
                        attempt(booking, notificationService::notifyCustomerBookingRescheduled);
                        attempt(booking, notificationService::notifySpaOwnerBookingRescheduled);
                    }
                }
            });
        } catch (RuntimeException ex) {
            log.warn("{} notification for booking {} was not sent: {}", event, bookingId, ex.getMessage());
        }
    }

    /** Skip a message that the booking's current state has already made untrue. */
    private boolean stillApplies(Booking booking, Event event) {
        BookingStatus status = booking.getStatus();
        return switch (event) {
            case CONFIRMED -> status == BookingStatus.CONFIRMED || status == BookingStatus.ARRIVED;
            case CANCELLED -> status == BookingStatus.CANCELLED;
            case RESCHEDULED -> status == BookingStatus.CONFIRMED || status == BookingStatus.PENDING_PAYMENT;
        };
    }

    private void attempt(Booking booking, Consumer<Booking> send) {
        try {
            send.accept(booking);
        } catch (RuntimeException ex) {
            log.warn("A notification for booking {} failed: {}", booking.getId(), ex.getMessage());
        }
    }
}
