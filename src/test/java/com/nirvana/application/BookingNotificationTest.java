package com.nirvana.application;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.Service;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.User;
import com.nirvana.application.model.enums.BookingPaymentType;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.PaymentMode;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.service.NotificationService;
import com.nirvana.application.service.impl.BookingEmailContent;
import com.nirvana.application.service.impl.NotificationSchedulingServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Booking emails: when they are sent, to whom, and what they say. No Spring context needed. */
class BookingNotificationTest {
    NotificationService sender = mock(NotificationService.class);
    BookingRepository bookings = mock(BookingRepository.class);
    TaskScheduler scheduler = mock(TaskScheduler.class);
    com.nirvana.application.service.SpaBookingAlerts spaAlerts = mock(com.nirvana.application.service.SpaBookingAlerts.class);
    NotificationSchedulingServiceImpl service =
            new NotificationSchedulingServiceImpl(sender, scheduler, bookings, mock(PlatformTransactionManager.class), spaAlerts);
    Booking booking;

    @BeforeEach void setUp() {
        // Run queued work immediately so each test sees the outcome.
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(call -> {
            ((Runnable) call.getArgument(0)).run();
            return null;
        });
        booking = booking(BookingStatus.CONFIRMED);
        when(bookings.findById(7L)).thenReturn(Optional.of(booking));
    }
    @AfterEach void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    static Booking booking(BookingStatus status) {
        Spa spa = mock(Spa.class);
        when(spa.getName()).thenReturn("Lotus & Stone");
        when(spa.getTimezone()).thenReturn("Asia/Kolkata");
        User user = mock(User.class);
        when(user.getName()).thenReturn("Asha <b>Rao</b>");
        Service treatment = mock(Service.class);
        when(treatment.getName()).thenReturn("Deep tissue <script>alert(1)</script>");
        Booking b = new Booking();
        b.setId(7L);
        b.setBookingReference("REF123");
        b.setStatus(status);
        b.setSpa(spa);
        b.setUser(user);
        b.setService(treatment);
        b.setGuestCount(2);
        b.setPaymentMode(PaymentMode.OFFLINE);
        b.setPaymentType(BookingPaymentType.STANDARD);
        b.setStartTs(OffsetDateTime.parse("2026-12-01T04:30:00Z"));
        return b;
    }

    @Test void confirmationGoesToCustomerAndSpaImmediately() {
        service.scheduleBookingNotifications(booking);
        verify(sender).notifyCustomerBookingConfirmed(booking);
        verify(sender).notifySpaOwnerBookingConfirmed(booking);
    }

    @Test void nothingIsSentUntilTheTransactionCommits() {
        TransactionSynchronizationManager.initSynchronization();
        service.scheduleBookingNotifications(booking);
        verifyNoInteractions(sender);
        // A rollback never calls afterCommit, so reaching here without a send covers that case too.
        List<TransactionSynchronization> registered = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, registered.size());
        registered.get(0).afterCommit();
        verify(sender).notifyCustomerBookingConfirmed(booking);
    }

    @Test void confirmationIsDroppedIfTheBookingWasCancelledInTheMeantime() {
        booking.setStatus(BookingStatus.CANCELLED);
        service.scheduleBookingNotifications(booking);
        verifyNoInteractions(sender);
    }

    @Test void cancellationTellsBothSidesButAnUnpaidExpiryTellsOnlyTheCustomer() {
        booking.setStatus(BookingStatus.CANCELLED);
        service.bookingCancelled(booking);
        verify(sender).notifyCustomerBookingCancelled(booking);
        verify(sender).notifySpaOwnerBookingCancelled(booking);

        clearInvocations(sender);
        booking.setCancellationReasonCode("PAYMENT_TIMEOUT");
        service.bookingCancelled(booking);
        verify(sender).notifyCustomerBookingCancelled(booking);
        verify(sender, never()).notifySpaOwnerBookingCancelled(any());
    }

    @Test void rescheduleTellsBothSides() {
        service.bookingRescheduled(booking);
        verify(sender).notifyCustomerBookingRescheduled(booking);
        verify(sender).notifySpaOwnerBookingRescheduled(booking);
    }

    @Test void aFailingEmailNeverBreaksTheCallerAndTheOtherEmailStillGoes() {
        doThrow(new IllegalStateException("smtp down")).when(sender).notifyCustomerBookingConfirmed(any());
        assertDoesNotThrow(() -> service.scheduleBookingNotifications(booking));
        verify(sender).notifySpaOwnerBookingConfirmed(booking);
    }

    @Test void emailsEscapeBookingDataAndUseTheSpaTimeZone() {
        var email = BookingEmailContent.customerConfirmed(booking, "Your booking is confirmed");
        assertEquals("BOOKING_CONFIRMED", email.templateCode());
        assertEquals("Your booking is confirmed - REF123", email.subject());
        assertFalse(email.html().contains("<script>"));
        assertFalse(email.html().contains("<b>Rao</b>"));
        assertTrue(email.html().contains("Lotus &amp; Stone"));
        assertTrue(email.html().contains("10:00 IST"), email.html()); // 04:30 UTC in Asia/Kolkata
        assertTrue(email.html().contains("Guests"));
        assertTrue(email.html().contains("Payment is due at the spa"));
    }

    @Test void expiryEmailExplainsThatNoPaymentWasTaken() {
        booking.setCancellationReasonCode("PAYMENT_TIMEOUT");
        String html = BookingEmailContent.customerCancelled(booking).html();
        assertTrue(html.contains("payment was not completed"));
        assertTrue(html.contains("not been charged"));
        assertEquals("BOOKING_REMINDER", BookingEmailContent.customerReminder(booking).templateCode());
    }
}
