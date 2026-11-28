package com.nirvana.application.service.calendar;

import com.nirvana.application.config.SendGridProperties;
import com.nirvana.application.model.Address;
import com.nirvana.application.model.Booking;
import com.nirvana.application.model.Service;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.User;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.PaymentMode;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.NotificationLogRepository;
import com.nirvana.application.service.impl.NoOpNotificationService;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The calendar invite text itself, and that guest emails carry it. No Spring context needed. */
class BookingCalendarTest {
    static final OffsetDateTime STAMP = OffsetDateTime.parse("2026-11-28T08:00:00Z");
    static final String URL = "https://nirvana.example/me/bookings";
    Booking booking;

    @BeforeEach void setUp() {
        Spa spa = mock(Spa.class);
        when(spa.getName()).thenReturn("Lotus, Stone; Spa");
        when(spa.getTimezone()).thenReturn("Asia/Kolkata");
        when(spa.getAddress()).thenReturn(Address.builder().addressLine("12 MG Road").locality("Indiranagar")
                .city("Bengaluru").state("Karnataka").postalCode("560038").country("India")
                .latitude(new BigDecimal("12.971600")).longitude(new BigDecimal("77.594600")).build());
        Service treatment = mock(Service.class);
        when(treatment.getName()).thenReturn("Deep tissue\\aromatherapy");
        User user = mock(User.class);
        when(user.getName()).thenReturn("Asha");
        when(user.getEmail()).thenReturn("asha@example.test");
        booking = new Booking();
        booking.setId(7L);
        booking.setBookingReference("REF123");
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setSpa(spa);
        booking.setService(treatment);
        booking.setUser(user);
        booking.setGuestCount(1);
        booking.setPaymentMode(PaymentMode.OFFLINE);
        booking.setStartTs(OffsetDateTime.parse("2026-12-01T10:00:00+05:30"));
        booking.setEndTs(OffsetDateTime.parse("2026-12-01T11:30:00+05:30"));
    }

    private String ics() {
        return BookingCalendar.render(booking, URL, "nirvana.example", null, null, STAMP);
    }

    /** Unfolds continuation lines (RFC 5545 3.1). */
    static List<String> lines(String ics) {
        return Arrays.asList(ics.replace("\r\n ", "").split("\r\n"));
    }

    @Test void confirmedBookingIsAPublishedEventInUtcWithAnAlarm() {
        String ics = ics();
        assertTrue(ics.endsWith("\r\n"));
        assertFalse(ics.replace("\r\n", "").contains("\n"), "every line ends in CRLF");
        var lines = lines(ics);
        assertEquals("BEGIN:VCALENDAR", lines.get(0));
        assertEquals("END:VCALENDAR", lines.get(lines.size() - 1));
        for (String expected : List.of("VERSION:2.0", "METHOD:PUBLISH", "UID:booking-REF123@nirvana.example", "SEQUENCE:0",
                "DTSTAMP:20261128T080000Z", "DTSTART:20261201T043000Z", "DTEND:20261201T060000Z", "STATUS:CONFIRMED",
                "BEGIN:VALARM", "TRIGGER:-PT2H", "ACTION:DISPLAY", "END:VALARM", "GEO:12.971600;77.594600", "URL:" + URL))
            assertTrue(lines.contains(expected), expected + " in\n" + ics);
        assertTrue(lines.contains("SUMMARY:Deep tissue\\\\aromatherapy at Lotus\\, Stone\\; Spa"), ics);
        assertTrue(lines.contains("LOCATION:Lotus\\, Stone\\; Spa\\, 12 MG Road\\, Indiranagar\\, Bengaluru\\, Karnataka\\, 560038\\, India"), ics);
        String description = lines.stream().filter(l -> l.startsWith("DESCRIPTION:Booking")).findFirst().orElseThrow();
        assertEquals("DESCRIPTION:Booking reference: REF123\\nReschedule or cancel in My bookings: " + URL, description);
        assertEquals(lines.indexOf("BEGIN:VEVENT") < lines.indexOf("BEGIN:VALARM"), true);
    }

    @Test void rescheduledBookingKeepsItsUidAndRaisesTheSequence() {
        String first = ics();
        booking.setNotificationRevision(2);
        booking.setStartTs(OffsetDateTime.parse("2026-12-02T15:00:00+05:30"));
        booking.setEndTs(OffsetDateTime.parse("2026-12-02T16:00:00+05:30"));
        var moved = lines(ics());
        assertTrue(moved.contains("SEQUENCE:2"));
        assertTrue(moved.contains("DTSTART:20261202T093000Z"));
        assertTrue(moved.contains("UID:booking-REF123@nirvana.example"));
        assertTrue(lines(first).contains("UID:booking-REF123@nirvana.example"));
    }

    @Test void cancelledBookingCancelsTheEventWithAHigherSequence() {
        booking.setNotificationRevision(1);
        booking.setStatus(BookingStatus.CANCELLED);
        var lines = lines(ics());
        assertTrue(lines.contains("METHOD:CANCEL"));
        assertTrue(lines.contains("STATUS:CANCELLED"));
        assertTrue(lines.contains("SEQUENCE:2"));
        assertTrue(lines.contains("UID:booking-REF123@nirvana.example"));
        assertFalse(lines.contains("BEGIN:VALARM"));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("DESCRIPTION:This booking was cancelled.\\n")));
    }

    @Test void textEscapesCommasSemicolonsBackslashesAndNewlines() {
        assertEquals("a\\, b\\; c\\\\ d\\ne\\nf", BookingCalendar.text("a, b; c\\ d\r\ne\nf"));
    }

    @Test void longLinesFoldAt75OctetsWithoutSplittingCharacters() {
        when(booking.getService().getName()).thenReturn("आयुर्वेदिक अभ्यंग मालिश और शिरोधारा ".repeat(4) + "with a long English tail");
        String ics = ics();
        for (String line : ics.split("\r\n")) {
            assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= 75, line);
            assertFalse(line.isEmpty());
        }
        // Folding is lossless: unfolded, the summary is the original text.
        assertTrue(lines(ics).stream().anyMatch(l -> l.equals("SUMMARY:" + booking.getService().getName() + " at Lotus\\, Stone\\; Spa")));
        assertFalse(new String(ics.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8).contains("�"));
        String exact = "X".repeat(75);
        assertEquals(exact, BookingCalendar.fold(exact));
        assertEquals("X".repeat(75) + "\r\n X", BookingCalendar.fold(exact + "X"));
    }

    @Test void organizerIsTheSenderWhenOneIsConfigured() {
        var lines = lines(BookingCalendar.render(booking, URL, "nirvana.example", "bookings@nirvana.example", "Nirvana \"Spa\"", STAMP));
        assertTrue(lines.contains("ORGANIZER;CN=\"Nirvana Spa\":mailto:bookings@nirvana.example"), lines.toString());
    }

    @Test void addressFallsBackToTheFormattedAddressOrTheSpaName() {
        when(booking.getSpa().getAddress()).thenReturn(Address.builder().formattedAddress("Lotus, Stone; Spa, 1 Main St, Pune").build());
        assertEquals("Lotus, Stone; Spa, 1 Main St, Pune", BookingCalendar.location(booking.getSpa()));
        when(booking.getSpa().getAddress()).thenReturn(null);
        assertEquals("Lotus, Stone; Spa", BookingCalendar.location(booking.getSpa()));
    }

    private BookingCalendar calendar() {
        var props = new SendGridProperties();
        return new BookingCalendar(mock(BookingRepository.class), props, "https://nirvana.example/");
    }

    @Test void attachmentIsLeftOffWhileUnpaidAndForUnpaidExpiry() {
        var calendar = calendar();
        var file = calendar.emailAttachment(booking);
        assertEquals("nirvana-booking-REF123.ics", file.fileName());
        assertEquals("text/calendar; charset=utf-8; method=PUBLISH", file.contentType());
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        assertNull(calendar.emailAttachment(booking));
        booking.setStatus(BookingStatus.CANCELLED);
        assertEquals("text/calendar; charset=utf-8; method=CANCEL", calendar.emailAttachment(booking).contentType());
        booking.setCancellationReasonCode("PAYMENT_TIMEOUT");
        assertNull(calendar.emailAttachment(booking));
    }

    private List<Part> attachments(MimeMessage message) throws Exception {
        message.saveChanges();
        var out = new ByteArrayOutputStream();
        message.writeTo(out);
        var parsed = new MimeMessage(Session.getInstance(new Properties()), new java.io.ByteArrayInputStream(out.toByteArray()));
        var found = new ArrayList<Part>();
        collect(parsed, found);
        return found;
    }

    private void collect(Part part, List<Part> found) throws Exception {
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) collect(multipart.getBodyPart(i), found);
        } else if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
            found.add(part);
        }
    }

    @Test void guestEmailsCarryTheInviteAndSpaEmailsDoNot() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        var props = new SendGridProperties();
        props.setOwnerFallbackEmail("owner@example.test");
        var service = new NoOpNotificationService(mock(NotificationLogRepository.class), sender, props, calendar());
        var sent = ArgumentCaptor.forClass(MimeMessage.class);

        service.notifyCustomerBookingConfirmed(booking);
        service.notifySpaOwnerBookingConfirmed(booking);
        booking.setNotificationRevision(1);
        service.notifyCustomerBookingRescheduled(booking);
        booking.setStatus(BookingStatus.CANCELLED);
        service.notifyCustomerBookingCancelled(booking);
        verify(sender, times(4)).send(sent.capture());

        var confirmed = attachments(sent.getAllValues().get(0));
        assertEquals(1, confirmed.size());
        assertEquals("nirvana-booking-REF123.ics", confirmed.get(0).getFileName());
        assertTrue(confirmed.get(0).getContentType().toLowerCase().startsWith("text/calendar"));
        String body = new String(confirmed.get(0).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(body.contains("METHOD:PUBLISH") && body.contains("SEQUENCE:0"), body);

        assertTrue(attachments(sent.getAllValues().get(1)).isEmpty(), "the spa gets no guest invite");

        String moved = new String(attachments(sent.getAllValues().get(2)).get(0).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(moved.contains("SEQUENCE:1") && moved.contains("METHOD:PUBLISH"), moved);

        String cancelled = new String(attachments(sent.getAllValues().get(3)).get(0).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(cancelled.contains("METHOD:CANCEL") && cancelled.contains("SEQUENCE:2"), cancelled);
    }
}
