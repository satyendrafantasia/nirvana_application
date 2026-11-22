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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Booking emails: when they are sent, to whom, and what they say. No Spring context needed. */
class BookingNotificationTest {
    NotificationService sender = mock(NotificationService.class);
    BookingRepository bookings = mock(BookingRepository.class);
    org.springframework.jdbc.core.JdbcTemplate jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
    NotificationSchedulingServiceImpl service = new NotificationSchedulingServiceImpl(sender, jdbc, bookings,
            mock(PlatformTransactionManager.class), mock(com.nirvana.application.service.SpaBookingAlerts.class));
    Booking booking;

    @BeforeEach void setUp() {
        booking = booking(BookingStatus.CONFIRMED);
        when(bookings.findById(7L)).thenReturn(Optional.of(booking));
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

    @Test void confirmationQueuesEachRecipientWithoutSendingInsideBookingTransaction() {
        service.scheduleBookingNotifications(booking);
        for (String audience : List.of("CUSTOMER_EMAIL", "SPA_EMAIL", "SPA_MOBILE"))
            verify(jdbc).update(contains("INSERT INTO booking_alert_job"), eq(7L), eq("CONFIRMED"), eq(audience), eq(0));
        verifyNoInteractions(sender);
    }

    @Test void unpaidExpiryQueuesOnlyCustomer() {
        booking.setCancellationReasonCode("PAYMENT_TIMEOUT");
        service.bookingCancelled(booking);
        verify(jdbc).update(contains("INSERT INTO booking_alert_job"), eq(7L), eq("CANCELLED"), eq("CUSTOMER_EMAIL"), eq(0));
        verifyNoMoreInteractions(jdbc);
    }

    private void pendingCustomerEmail() {
        when(jdbc.queryForList(contains("SELECT id FROM booking_alert_job"), eq(Long.class))).thenReturn(List.of(12L));
        when(jdbc.queryForList(contains("FOR UPDATE"), eq(12L))).thenReturn(List.of(java.util.Map.of(
                "booking_id",7L,"revision",0,"event_kind","CONFIRMED","audience","CUSTOMER_EMAIL")));
    }

    @Test void transientFailureIsRetriedFromThePersistentJob() {
        pendingCustomerEmail();
        doThrow(new IllegalStateException("Temporary mail failure")).doNothing().when(sender).notifyCustomerBookingConfirmed(booking);
        service.drain();
        verify(jdbc).update(contains("attempts=attempts+1"), eq(12L));
        service.drain();
        verify(jdbc).update(contains("SET state=?"), eq("DONE"), eq(12L));
        verify(sender, times(2)).notifyCustomerBookingConfirmed(booking);
        verifyNoMoreInteractions(sender);
    }

    @Test void obsoleteConfirmationIsSuppressedAfterCancellation() {
        pendingCustomerEmail();
        booking.setStatus(BookingStatus.CANCELLED);
        service.drain();
        verify(jdbc).update(contains("SET state=?"), eq("OBSOLETE"), eq(12L));
        verifyNoInteractions(sender);
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
