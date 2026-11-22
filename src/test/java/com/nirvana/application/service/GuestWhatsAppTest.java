package com.nirvana.application.service;

import com.nirvana.application.model.Address;
import com.nirvana.application.model.Booking;
import com.nirvana.application.model.Service;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.User;
import com.nirvana.application.model.enums.BookingPaymentType;
import com.nirvana.application.model.enums.PaymentMode;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Guest booking updates on WhatsApp: who gets them, and what each template is filled with. */
class GuestWhatsAppTest {
    private final SpaAlertWhatsApp whatsApp = mock(SpaAlertWhatsApp.class);

    private GuestWhatsApp guest(boolean enabled) {
        when(whatsApp.configured()).thenReturn(true);
        return new GuestWhatsApp(whatsApp, enabled, "nirvana_guest_booking_confirmed", "nirvana_guest_booking_reminder",
                "nirvana_guest_booking_moved", "nirvana_guest_booking_cancelled");
    }

    private static Booking booking(boolean agreed) {
        User u = new User();
        u.setPhone("+91 98765 43210");
        u.setPhoneVerified(true);
        if (agreed) u.setWhatsappUpdatesAt(OffsetDateTime.parse("2026-11-20T10:00:00Z"));
        Spa spa = new Spa();
        spa.setName("The Fern House");
        spa.setTimezone("Asia/Kolkata");
        Address a = new Address();
        a.setAddressLine("12 Vibhuti Khand");
        a.setLocality("Gomti Nagar");
        a.setCity("Lucknow");
        spa.setAddress(a);
        Service s = new Service();
        s.setName("Aromatherapy Massage");
        Booking b = new Booking();
        b.setBookingReference("7451CD21648D");
        b.setUser(u);
        b.setSpa(spa);
        b.setService(s);
        b.setStartTs(OffsetDateTime.parse("2026-11-25T13:30:00Z"));
        b.setPaymentType(BookingPaymentType.STANDARD);
        b.setPaymentMode(PaymentMode.OFFLINE);
        b.setRemainderCents(199900);
        return b;
    }

    @Test void onlyGuestsWhoAgreedWithAVerifiedMobileGetThem() {
        assertEquals(Optional.of("+919876543210"), guest(true).recipient(booking(true)));
        assertEquals(Optional.empty(), guest(true).recipient(booking(false)));
        assertEquals(Optional.empty(), guest(false).recipient(booking(true)), "switched off");
        Booking unverified = booking(true);
        unverified.getUser().setPhoneVerified(false);
        assertEquals(Optional.empty(), guest(true).recipient(unverified));
        assertFalse(guest(true).send(booking(false), GuestWhatsApp.Kind.CONFIRMED));
        verify(whatsApp, never()).sendTemplate(any(), any(), any(), any(), any());
    }

    @Test void theReminderCarriesTheAddressAndWhatToPay() {
        assertTrue(guest(true).send(booking(true), GuestWhatsApp.Kind.REMINDER));
        verify(whatsApp).sendTemplate(eq("+919876543210"), eq("nirvana_guest_booking_reminder"), eq(List.of(
                "Aromatherapy Massage", "The Fern House", "Wed 25 Nov 2026, 19:00 IST",
                "12 Vibhuti Khand, Gomti Nagar, Lucknow", "Please pay Rs 1,999 at the spa.", "7451CD21648D")), isNull(), anyString());
    }

    @Test void eachUpdateUsesItsOwnTemplate() {
        var g = guest(true);
        g.send(booking(true), GuestWhatsApp.Kind.CONFIRMED);
        g.send(booking(true), GuestWhatsApp.Kind.MOVED);
        g.send(booking(true), GuestWhatsApp.Kind.CANCELLED);
        verify(whatsApp).sendTemplate(any(), eq("nirvana_guest_booking_confirmed"), eq(List.of("7451CD21648D", "Aromatherapy Massage",
                "The Fern House", "Wed 25 Nov 2026, 19:00 IST", "Please pay Rs 1,999 at the spa.")), isNull(), any());
        verify(whatsApp).sendTemplate(any(), eq("nirvana_guest_booking_moved"), anyList(), isNull(), any());
        verify(whatsApp).sendTemplate(any(), eq("nirvana_guest_booking_cancelled"), anyList(), isNull(), any());
    }

    @Test void paymentLines() {
        Booking b = booking(true);
        b.setPaymentMode(PaymentMode.ONLINE);
        assertEquals("Paid online.", GuestWhatsApp.payment(b));
        b.setPaymentType(BookingPaymentType.PAID_BY_PACKAGE);
        assertEquals("Paid with your Nirvana package.", GuestWhatsApp.payment(b));
    }
}
