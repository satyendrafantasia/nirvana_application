package com.nirvana.application.service.impl;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.Payment;
import com.nirvana.application.model.Slot;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.CancellationActor;
import com.nirvana.application.model.enums.PaymentStatus;
import com.nirvana.application.model.enums.SlotStatus;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.PaymentRepository;
import com.nirvana.application.repository.SlotRepository;
import com.nirvana.application.service.NotificationService;
import com.nirvana.application.service.WaitlistService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Moves a booking through the states that follow confirmation: unpaid bookings expire and give
 * their slot back, and the spa records arrival, completion or a no-show.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingLifecycleService {

    static final String PAYMENT_TIMEOUT = "PAYMENT_TIMEOUT";
    /** A payment link stays payable until this long before the appointment. */
    private static final int PAYMENT_LINK_CUTOFF_MINUTES = 30;

    private final BookingRepository bookingRepository;
    private final SlotRepository slotRepository;
    private final PaymentRepository paymentRepository;
    private final WaitlistService waitlistService;
    private final NotificationService notificationService;
    private final com.nirvana.application.service.GuestWhatsApp guestWhatsApp;
    private final com.nirvana.application.service.NotificationSchedulingService notifications;
    private final LoyaltyService loyalty;

    @Value("${reliability.bookings.pending-payment-expiry-minutes:30}")
    private int pendingPaymentExpiryMinutes;

    @Value("${reliability.bookings.arrival-window-minutes:120}")
    private int arrivalWindowMinutes;

    public record StatusChange(Long bookingId, String bookingReference, BookingStatus status) {
    }

    // ------------------ UNPAID BOOKINGS ------------------

    /**
     * Cancels an online booking that was never paid and releases its slot capacity.
     * Returns false when the booking is no longer pending or is still inside its payment window.
     * A capture that arrives afterwards is still recorded and flagged for refund by the payment flow.
     */
    @Transactional
    public boolean expireUnpaid(Long bookingId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            return false;
        }

        var payments = paymentRepository.findByBookingId(bookingId);
        boolean captured = payments.stream().anyMatch(p -> p.getPaymentStatus() == PaymentStatus.CAPTURED
                || p.getPaymentStatus() == PaymentStatus.COMPLETED);
        if (captured) {
            return false; // settlement is in progress; never cancel a paid booking here
        }

        boolean started = !booking.getStartTs().isAfter(now);
        boolean hasPaymentLink = payments.stream().anyMatch(p -> p.getPaymentLinkId() != null);
        OffsetDateTime deadline = hasPaymentLink
                ? booking.getStartTs().minusMinutes(PAYMENT_LINK_CUTOFF_MINUTES)
                : booking.getCreatedAt().plusMinutes(pendingPaymentExpiryMinutes);
        if (!started && deadline.isAfter(now)) {
            return false;
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(now);
        booking.setCancelledBy(CancellationActor.SYSTEM);
        booking.setCancellationReasonCode(PAYMENT_TIMEOUT);
        booking.setCancellationReasonText("Payment was not completed in time");
        booking.setLastStatusChangedAt(now);
        loyalty.returnRedeemed(booking);
        bookingRepository.save(booking);

        for (Payment payment : payments) {
            if (payment.getPaymentStatus() == PaymentStatus.INIT || payment.getPaymentStatus() == PaymentStatus.PENDING) {
                payment.setPaymentStatus(PaymentStatus.CANCELLED);
                paymentRepository.save(payment);
            }
        }

        Slot slot = slotRepository.findByIdForUpdate(booking.getSlot().getId())
                .orElseThrow(() -> new EntityNotFoundException("Slot not found for booking"));
        int updated = Math.max(0, slot.getBookedUnits() - booking.getGuestCount());
        slot.setBookedUnits((short) updated);
        if (slot.getStatus() == SlotStatus.BOOKED && updated < slot.getCapacityUnit()) {
            slot.setStatus(SlotStatus.OPEN);
        }
        slotRepository.save(slot);

        try {
            waitlistService.tryNotifySlotAvailable(slot);
            notifications.bookingCancelled(booking);
        } catch (RuntimeException ex) {
            log.warn("Unpaid booking {} expired but a notification could not be sent: {}", bookingId, ex.getMessage());
        }
        log.info("Expired unpaid booking {} and released {} unit(s) on slot {}", bookingId, booking.getGuestCount(), slot.getId());
        return true;
    }

    // ------------------ REMINDERS ------------------

    /**
     * Sends the appointment reminder once. The marker is written in the same transaction, so a
     * restart or a second instance cannot send it again. Returns true only when an email was attempted.
     */
    @Transactional
    public boolean sendReminder(Long bookingId, int leadHours) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.CONFIRMED
                || booking.getReminderProcessedAt() != null
                || !booking.getStartTs().isAfter(now)
                || booking.getStartTs().isAfter(now.plusHours(leadHours))) {
            return false;
        }
        booking.setReminderProcessedAt(now);
        bookingRepository.save(booking);

        // Someone who booked inside the reminder window has just received the confirmation.
        OffsetDateTime confirmedAt = booking.getLastStatusChangedAt() != null
                ? booking.getLastStatusChangedAt() : booking.getCreatedAt();
        if (confirmedAt != null && confirmedAt.isAfter(booking.getStartTs().minusHours(leadHours))) {
            return false;
        }
        try {
            notificationService.notifyCustomerBookingReminder(booking);
        } catch (RuntimeException ex) {
            log.warn("Reminder for booking {} could not be sent: {}", bookingId, ex.getMessage());
        }
        try {
            guestWhatsApp.send(booking, com.nirvana.application.service.GuestWhatsApp.Kind.REMINDER);
        } catch (RuntimeException ex) {
            log.warn("WhatsApp reminder for booking {} could not be sent: {}", bookingId, ex.getMessage());
        }
        return true;
    }

    // ------------------ SPA ACTIONS ------------------

    @Transactional
    public StatusChange markArrived(Long spaId, Long bookingId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Booking booking = lockForSpa(spaId, bookingId);
        requireStatus(booking, "checked in", BookingStatus.CONFIRMED);
        if (now.isBefore(booking.getStartTs().minusMinutes(arrivalWindowMinutes))) {
            throw new IllegalStateException("Check-in opens " + arrivalWindowMinutes + " minutes before the appointment");
        }
        booking.setStatus(BookingStatus.ARRIVED);
        booking.setArrivedAt(now);
        return saved(booking, now);
    }

    @Transactional
    public StatusChange markCompleted(Long spaId, Long bookingId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Booking booking = lockForSpa(spaId, bookingId);
        requireStatus(booking, "completed", BookingStatus.CONFIRMED, BookingStatus.ARRIVED);
        requireStarted(booking, now, "completed");
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(now);
        loyalty.awardFor(booking);
        return saved(booking, now);
    }

    @Transactional
    public StatusChange markNoShow(Long spaId, Long bookingId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Booking booking = lockForSpa(spaId, bookingId);
        requireStatus(booking, "marked as a no-show", BookingStatus.CONFIRMED);
        requireStarted(booking, now, "marked as a no-show");
        booking.setStatus(BookingStatus.NO_SHOW);
        booking.setNoShowMarkedAt(now);
        return saved(booking, now);
    }

    /**
     * Closes an appointment the spa never closed out. Returns false if it was already handled.
     */
    @Transactional
    public boolean autoComplete(Long bookingId, OffsetDateTime endedBefore) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null
                || booking.getStatus() != BookingStatus.ARRIVED
                || !booking.getEndTs().isBefore(endedBefore)) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(now);
        loyalty.awardFor(booking);
        saved(booking, now);
        return true;
    }

    // ------------------ INTERNAL ------------------

    private Booking lockForSpa(Long spaId, Long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));
        if (!booking.getSpa().getId().equals(spaId)) {
            // Same answer as a missing booking so one spa cannot probe another spa's bookings.
            throw new EntityNotFoundException("Booking not found");
        }
        return booking;
    }

    private void requireStatus(Booking booking, String action, BookingStatus... allowed) {
        for (BookingStatus status : allowed) {
            if (booking.getStatus() == status) {
                return;
            }
        }
        throw new IllegalStateException("A booking that is " + booking.getStatus().name().toLowerCase().replace('_', ' ')
                + " cannot be " + action);
    }

    private void requireStarted(Booking booking, OffsetDateTime now, String action) {
        if (now.isBefore(booking.getStartTs())) {
            throw new IllegalStateException("A booking cannot be " + action + " before its start time");
        }
    }

    private StatusChange saved(Booking booking, OffsetDateTime now) {
        booking.setLastStatusChangedAt(now);
        bookingRepository.save(booking);
        return new StatusChange(booking.getId(), booking.getBookingReference(), booking.getStatus());
    }
}
