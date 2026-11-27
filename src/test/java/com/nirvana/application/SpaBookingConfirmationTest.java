package com.nirvana.application;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.service.SpaAlertSms;
import com.nirvana.application.service.SpaBookingAlerts;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spas confirm or decline each booking from an SMS link; one last reminder, then it stands. */
@SpringBootTest @AutoConfigureMockMvc @Transactional
class SpaBookingConfirmationTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean SpaAlertSms sms;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired BookingRepository bookings;
    @Autowired SpaBookingAlerts alerts;
    @Autowired EntityManager em;

    /** A confirmed pay-at-spa booking tomorrow at a spa with the given phone. */
    private Booking booking(String phone) {
        Booking booking = BookingFixtures.payAtSpa(em, OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), BookingStatus.CONFIRMED);
        booking.getSpa().setPhone(phone);
        return booking;
    }

    private String token(long bookingId) {
        return jdbc.queryForObject("SELECT token FROM spa_booking_confirmation WHERE booking_id=?", String.class, bookingId);
    }

    private String state(long bookingId) {
        return jdbc.queryForObject("SELECT status FROM spa_booking_confirmation WHERE booking_id=?", String.class, bookingId);
    }

    @Autowired com.nirvana.application.service.RefundService refunds;

    @Test void retryingAWalletRefundLeavesOneCreditForTheCustomer() {
        var b = booking("9876543210");
        var p = new com.nirvana.application.model.Payment();
        p.setPaymentStatus(com.nirvana.application.model.enums.PaymentStatus.CAPTURED);
        p.setCurrency("INR");
        for (int i = 0; i < 2; i++) refunds.processRefund(p, b, com.nirvana.application.model.enums.RefundRoute.WALLET_CREDIT, 150000);
        assertEquals(150000, jdbc.queryForObject("SELECT SUM(amount_cents) FROM booking_wallet_credit WHERE booking_id=? AND user_id=?",
                Integer.class, b.getId(), b.getUser().getId()));
    }

    @Test void onlyIndianMobilesGetSms() {
        assertEquals(Optional.of("+919876543210"), SpaAlertSms.mobile("+91 98765 43210"));
        assertEquals(Optional.of("+919876543210"), SpaAlertSms.mobile("098765 43210"));
        assertEquals(Optional.empty(), SpaAlertSms.mobile("+91 124 423 4441"), "Gurugram landline");
        assertEquals(Optional.empty(), SpaAlertSms.mobile(null));
    }

    @Test void theSpaHasTwoHoursOrUntilAnHourBeforeTheStart() throws Exception {
        Method window = SpaBookingAlerts.class.getDeclaredMethod("window", Instant.class, Instant.class);
        window.setAccessible(true);
        Instant now = Instant.parse("2026-11-22T10:00:00Z");
        Object tomorrow = window.invoke(null, now, now.plus(Duration.ofDays(1)));
        assertTrue(tomorrow.toString().contains("reminder=2026-11-22T10:30:00Z"), tomorrow.toString());
        assertTrue(tomorrow.toString().contains("deadline=2026-11-22T12:00:00Z"), tomorrow.toString());
        Object soon = window.invoke(null, now, now.plus(Duration.ofMinutes(150)));
        assertTrue(soon.toString().contains("deadline=2026-11-22T11:30:00Z"), "an hour before a 12:30 start: " + soon);
        Object verySoon = window.invoke(null, now, now.plus(Duration.ofMinutes(40)));
        assertTrue(verySoon.toString().contains("reminder=2026-11-22T10:10:00Z") && verySoon.toString().contains("deadline=2026-11-22T10:20:00Z"),
                "never less than 20 minutes to answer: " + verySoon);
    }

    @Test void theSpaConfirmsFromItsSmsLinkWithoutSigningIn() throws Exception {
        Booking b = booking("+91 98765 43210");
        alerts.bookingConfirmed(b);
        assertEquals("PENDING", state(b.getId()));
        assertEquals("+919876543210", jdbc.queryForObject("SELECT phone FROM spa_booking_confirmation WHERE booking_id=?", String.class, b.getId()));
        String token = token(b.getId());
        mvc.perform(get("/api/public/spa-bookings/" + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(b.getBookingReference()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.payment").value(org.hamcrest.Matchers.startsWith("guest pays Rs ")));
        mvc.perform(post("/api/public/spa-bookings/" + token + "/confirm")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        // A second alert for the same booking changes nothing.
        alerts.bookingConfirmed(b);
        assertEquals(token, token(b.getId()));
        mvc.perform(get("/api/public/spa-bookings/notARealToken12345")).andExpect(status().isNotFound());
    }

    @Test void decliningCancelsTheBookingForTheGuest() throws Exception {
        Booking b = booking("9876543210");
        alerts.bookingConfirmed(b);
        mvc.perform(post("/api/public/spa-bookings/" + token(b.getId()) + "/decline").contentType("application/json")
                        .content("{\"reason\":\"Fully booked at that time\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.bookingStatus").value("CANCELLED"));
        assertEquals("SPA", jdbc.queryForObject("SELECT cancelled_by FROM booking WHERE id=?", String.class, b.getId()));
        assertEquals("Fully booked at that time", jdbc.queryForObject("SELECT decline_reason FROM spa_booking_confirmation WHERE booking_id=?", String.class, b.getId()));
        // A cancelled booking can't be confirmed afterwards.
        mvc.perform(post("/api/public/spa-bookings/" + token(b.getId()) + "/confirm")).andExpect(status().isConflict());
    }

    @Test void oneLastReminderThenTheBookingStands() {
        Booking b = booking("+91 98765 43210");
        alerts.bookingConfirmed(b);
        jdbc.update("UPDATE spa_booking_confirmation SET reminder_due_at=UTC_TIMESTAMP() - INTERVAL 1 MINUTE WHERE booking_id=?", b.getId());
        alerts.runDue();
        assertTrue(alerts.remind(b));
        assertFalse(alerts.remind(b));
        assertNotNull(jdbc.queryForObject("SELECT reminder_sent_at FROM spa_booking_confirmation WHERE booking_id=?", Object.class, b.getId()));
        assertEquals("PENDING", state(b.getId()), "still the spa's to answer until the deadline");
        jdbc.update("UPDATE spa_booking_confirmation SET deadline_at=UTC_TIMESTAMP() - INTERVAL 1 MINUTE WHERE booking_id=?", b.getId());
        alerts.runDue();
        assertEquals("NEEDS_CONTACT", state(b.getId()));
        assertNull(jdbc.queryForObject("SELECT responded_at FROM spa_booking_confirmation WHERE booking_id=?",Object.class,b.getId()));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking_alert_job WHERE booking_id=? AND event_kind='FOLLOW_UP' AND state='NEEDS_CONTACT'",Integer.class,b.getId()));
        assertEquals("CONFIRMED", jdbc.queryForObject("SELECT status FROM booking WHERE id=?", String.class, b.getId()));
    }

    @Test void aSpaWithoutAMobileIsNotTexted() {
        Booking b = booking("+91 124 423 4441");
        assertThrows(com.nirvana.application.service.BookingContactUnavailable.class, () -> alerts.bookingConfirmed(b));
        assertEquals("NO_MOBILE", state(b.getId()));
        alerts.runDue();
        assertEquals("NO_MOBILE", state(b.getId()));
    }

    @Test void retryOfUnsentAlertReusesTheLinkAndRecordsDeliveryAfterSuccess() {
        Booking b = booking("+91 98765 43210");
        org.mockito.Mockito.doThrow(new com.nirvana.application.service.BookingContactUnavailable("SMS unavailable"))
            .doNothing().when(sms).send(org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq(SpaAlertSms.Kind.NEW),org.mockito.ArgumentMatchers.anyString());
        assertThrows(com.nirvana.application.service.BookingContactUnavailable.class, () -> alerts.bookingConfirmed(b));
        var first=token(b.getId());
        assertNull(jdbc.queryForObject("SELECT sent_at FROM spa_booking_confirmation WHERE booking_id=?",Object.class,b.getId()));
        alerts.bookingConfirmed(b);
        assertEquals(first,token(b.getId()));
        assertNotNull(jdbc.queryForObject("SELECT sent_at FROM spa_booking_confirmation WHERE booking_id=?",Object.class,b.getId()));
    }

    @Test void theGuestCancellingClosesTheRequest() {
        Booking b = booking("+91 98765 43210");
        alerts.bookingConfirmed(b);
        jdbc.update("UPDATE booking SET status='CANCELLED', cancelled_by='USER' WHERE id=?", b.getId());
        em.clear();
        alerts.bookingCancelled(bookings.findById(b.getId()).orElseThrow());
        assertEquals("CLOSED", state(b.getId()));
    }
}
