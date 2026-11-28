package com.nirvana.application.service.calendar;

import com.nirvana.application.config.SendGridProperties;
import com.nirvana.application.model.Address;
import com.nirvana.application.model.Booking;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.service.impl.BookingEmailContent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Calendar invites (RFC 5545) for a guest's booking: the file behind "Add to calendar" and the
 * attachment on booking emails. Every invite for a booking shares one UID, and SEQUENCE follows
 * the booking's notification revision, which rises on every reschedule, so calendar apps replace
 * the earlier copy instead of adding a second event. A cancelled booking is sent as METHOD:CANCEL.
 */
@Component
public class BookingCalendar {

    private static final DateTimeFormatter UTC_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final int MAX_OCTETS = 75;

    /** The file and how to serve it; contentType carries the iTIP method for email clients. */
    public record Ics(String fileName, String method, byte[] content) {
        public String contentType() {
            return "text/calendar; charset=utf-8; method=" + method;
        }
    }

    private final BookingRepository bookings;
    private final SendGridProperties mail;
    private final String frontendUrl;

    public BookingCalendar(BookingRepository bookings, SendGridProperties mail,
                           @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.bookings = bookings;
        this.mail = mail;
        this.frontendUrl = frontendUrl;
    }

    /** The invite for one of the guest's own bookings; empty for anyone else's, so it reads as not found. */
    @Transactional(readOnly = true)
    public Optional<Ics> forGuest(long bookingId, long userId) {
        return bookings.findByIdAndUserId(bookingId, userId).map(this::invite);
    }

    public Ics invite(Booking booking) {
        String text = render(booking, myBookingsUrl(), host(), mail.getFromEmail(), mail.getFromName(), OffsetDateTime.now(ZoneOffset.UTC));
        return new Ics("nirvana-booking-" + safeName(booking.getBookingReference()) + ".ics",
                cancelled(booking) ? "CANCEL" : "PUBLISH", text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The invite to attach to a guest email, or null when there is nothing worth adding: a booking still
     * waiting for payment may lapse, and one that lapsed unpaid never had an invite to withdraw.
     */
    public BookingEmailContent.Attachment emailAttachment(Booking booking) {
        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) return null;
        if (cancelled(booking) && "PAYMENT_TIMEOUT".equals(booking.getCancellationReasonCode())) return null;
        Ics ics = invite(booking);
        return new BookingEmailContent.Attachment(ics.fileName(), ics.contentType(), ics.content());
    }

    static String render(Booking b, String myBookingsUrl, String uidHost, String organizerEmail, String organizerName,
                         OffsetDateTime stamp) {
        boolean cancelled = cancelled(b);
        Spa spa = b.getSpa();
        String service = b.getService() != null && b.getService().getName() != null ? b.getService().getName() : "Spa appointment";
        String spaName = spa != null && spa.getName() != null && !spa.getName().isBlank() ? spa.getName() : null;
        OffsetDateTime start = b.getStartTs();
        OffsetDateTime end = b.getEndTs() != null && b.getEndTs().isAfter(start) ? b.getEndTs() : start.plusHours(1);

        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("VERSION:2.0");
        lines.add("PRODID:-//Nirvana//Spa bookings//EN");
        lines.add("CALSCALE:GREGORIAN");
        lines.add("METHOD:" + (cancelled ? "CANCEL" : "PUBLISH"));
        lines.add("BEGIN:VEVENT");
        lines.add("UID:" + uid(b, uidHost));
        lines.add("SEQUENCE:" + sequence(b));
        lines.add("DTSTAMP:" + utc(stamp));
        lines.add("DTSTART:" + utc(start));
        lines.add("DTEND:" + utc(end));
        lines.add("SUMMARY:" + text(spaName == null ? service : service + " at " + spaName));
        String location = location(spa);
        if (location != null) lines.add("LOCATION:" + text(location));
        Address address = spa != null ? spa.getAddress() : null;
        if (address != null && address.getLatitude() != null && address.getLongitude() != null) {
            lines.add("GEO:" + address.getLatitude().toPlainString() + ";" + address.getLongitude().toPlainString());
        }
        lines.add("DESCRIPTION:" + text(description(b, cancelled, myBookingsUrl)));
        if (myBookingsUrl != null) lines.add("URL:" + myBookingsUrl);
        if (organizerEmail != null && !organizerEmail.isBlank()) {
            String name = organizerName == null || organizerName.isBlank() ? "Nirvana" : organizerName;
            lines.add("ORGANIZER;CN=\"" + name.replaceAll("[\"\\p{Cntrl}]", "") + "\":mailto:" + organizerEmail.trim());
        }
        lines.add("STATUS:" + (cancelled ? "CANCELLED" : "CONFIRMED"));
        lines.add("TRANSP:OPAQUE");
        if (!cancelled) {
            lines.add("BEGIN:VALARM");
            lines.add("ACTION:DISPLAY");
            lines.add("DESCRIPTION:" + text(service + " in 2 hours"));
            lines.add("TRIGGER:-PT2H");
            lines.add("END:VALARM");
        }
        lines.add("END:VEVENT");
        lines.add("END:VCALENDAR");
        return lines.stream().map(BookingCalendar::fold).collect(Collectors.joining("\r\n", "", "\r\n"));
    }

    static boolean cancelled(Booking b) {
        return b.getStatus() == BookingStatus.CANCELLED;
    }

    /** Rises with each reschedule; a cancellation counts as one more change so it supersedes the last invite. */
    static int sequence(Booking b) {
        return Math.max(0, b.getNotificationRevision()) + (cancelled(b) ? 1 : 0);
    }

    static String uid(Booking b, String host) {
        String key = b.getBookingReference() != null ? b.getBookingReference() : String.valueOf(b.getId());
        return "booking-" + key.replaceAll("[^A-Za-z0-9._-]", "") + "@" + (host == null || host.isBlank() ? "nirvana" : host);
    }

    /** TEXT value escaping (RFC 5545 3.3.11): backslash, semicolon, comma and line breaks. */
    static String text(String value) {
        return value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\\n");
    }

    /** Folds a content line so no line exceeds 75 octets, never splitting a UTF-8 character (RFC 5545 3.1). */
    static String fold(String line) {
        StringBuilder out = new StringBuilder();
        int octets = 0;
        for (int i = 0; i < line.length(); ) {
            int cp = line.codePointAt(i);
            int size = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (octets + size > MAX_OCTETS) {
                out.append("\r\n ");
                octets = 1;
            }
            out.appendCodePoint(cp);
            octets += size;
            i += Character.charCount(cp);
        }
        return out.toString();
    }

    private static String description(Booking b, boolean cancelled, String myBookingsUrl) {
        StringBuilder d = new StringBuilder();
        if (cancelled) d.append("This booking was cancelled.\n");
        d.append("Booking reference: ").append(b.getBookingReference());
        if (b.getGuestCount() != null && b.getGuestCount() > 1) d.append("\nGuests: ").append(b.getGuestCount());
        if (myBookingsUrl != null) {
            d.append(cancelled ? "\nYour bookings: " : "\nReschedule or cancel in My bookings: ").append(myBookingsUrl);
        }
        return d.toString();
    }

    static String location(Spa spa) {
        if (spa == null) return null;
        Address a = spa.getAddress();
        String place = null;
        if (a != null) {
            if (a.getFormattedAddress() != null && !a.getFormattedAddress().isBlank()) {
                place = a.getFormattedAddress().trim();
            } else {
                place = Stream.of(a.getAddressLine(), a.getAddressLine2(), a.getLocality(), a.getCity(), a.getState(), a.getPostalCode(), a.getCountry())
                        .filter(part -> part != null && !part.isBlank()).map(String::trim).distinct()
                        .collect(Collectors.joining(", "));
                if (place.isEmpty()) place = null;
            }
        }
        String name = spa.getName() == null || spa.getName().isBlank() ? null : spa.getName().trim();
        if (place == null) return name;
        return name == null || place.startsWith(name) ? place : name + ", " + place;
    }

    private static String utc(OffsetDateTime time) {
        return time.withOffsetSameInstant(ZoneOffset.UTC).format(UTC_STAMP);
    }

    private static String safeName(String reference) {
        String cleaned = reference == null ? "" : reference.replaceAll("[^A-Za-z0-9-]", "");
        return cleaned.isEmpty() ? "appointment" : cleaned;
    }

    private String myBookingsUrl() {
        return frontendUrl == null || frontendUrl.isBlank() ? null : frontendUrl.replaceAll("/+$", "") + "/me/bookings";
    }

    private String host() {
        try {
            return URI.create(frontendUrl).getHost();
        } catch (RuntimeException invalid) {
            return null;
        }
    }
}
