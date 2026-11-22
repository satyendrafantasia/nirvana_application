package com.nirvana.application.service.impl;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.User;
import org.springframework.web.util.HtmlUtils;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Subject and HTML body for every booking email, shared by all delivery channels so the wording
 * stays the same whichever provider sends it. Every value taken from a booking is HTML-escaped.
 */
public final class BookingEmailContent {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE dd MMM yyyy, HH:mm z");

    private BookingEmailContent() {
    }

    public record Email(String templateCode, String subject, String html) {
    }

    public static Email customerConfirmed(Booking b, String subject) {
        return new Email("BOOKING_CONFIRMED", subject + " - " + b.getBookingReference(),
                greeting(b) + "<p>Your booking is confirmed.</p>" + summary(b, false)
                        + (payAtSpa(b) ? "<p>Payment is due at the spa.</p>" : "")
                        + "<p>We look forward to seeing you!</p>");
    }

    public static Email ownerConfirmed(Booking b, String subject) {
        return new Email("BOOKING_CONFIRMED_SPA", subject + " - " + b.getBookingReference(),
                "<p>Hello,</p><p>A new booking has been confirmed.</p>" + summary(b, true)
                        + "<p>Please prepare to welcome the guest.</p>");
    }

    public static Email customerCancelled(Booking b) {
        boolean unpaid = BookingLifecycleService.PAYMENT_TIMEOUT.equals(b.getCancellationReasonCode());
        return new Email("BOOKING_CANCELLED", "Your booking was cancelled - " + b.getBookingReference(),
                greeting(b) + (unpaid
                        ? "<p>Your booking was cancelled because payment was not completed in time. "
                        + "You have not been charged. You are welcome to book again.</p>"
                        : bySpa(b)
                        ? "<p>We are sorry: the spa had to cancel your booking.</p>"
                        + (b.getCancellationReasonText() == null || b.getCancellationReasonText().isBlank() ? ""
                        : "<p>Reason given: " + esc(b.getCancellationReasonText()) + "</p>")
                        + (b.getPaymentType() == com.nirvana.application.model.enums.BookingPaymentType.PAID_BY_PACKAGE
                        ? "<p>Your package session has been returned to you.</p>"
                        : payAtSpa(b) ? "" : "<p>Anything you paid is being refunded in full to your original payment method.</p>")
                        : "<p>Your booking was cancelled.</p>")
                        + summary(b, false));
    }

    public static Email ownerCancelled(Booking b) {
        return new Email("BOOKING_CANCELLED_SPA", "Booking cancelled - " + b.getBookingReference(),
                "<p>Hello,</p><p>A booking was cancelled. The time is available again.</p>" + summary(b, true));
    }

    public static Email customerRescheduled(Booking b) {
        return new Email("BOOKING_RESCHEDULED", "Your booking has a new time - " + b.getBookingReference(),
                greeting(b) + "<p>Your booking was moved. The new details are below.</p>" + summary(b, false));
    }

    public static Email ownerRescheduled(Booking b) {
        return new Email("BOOKING_RESCHEDULED_SPA", "Booking moved - " + b.getBookingReference(),
                "<p>Hello,</p><p>A guest moved their booking. The new details are below.</p>" + summary(b, true));
    }

    public static Email customerReminder(Booking b) {
        return new Email("BOOKING_REMINDER", "Reminder: your appointment - " + b.getBookingReference(),
                greeting(b) + "<p>This is a reminder of your upcoming appointment.</p>" + summary(b, false)
                        + (payAtSpa(b) ? "<p>Payment is due at the spa.</p>" : "")
                        + "<p>If your plans have changed, you can reschedule or cancel from My bookings.</p>");
    }

    /**
     * A start time in the spa's time zone, or India's when the spa has none set. An offset alone
     * has no zone name, so it can't be printed with the zone pattern directly.
     */
    public static String formatStart(OffsetDateTime start, String timezone) {
        if (start == null) {
            return "N/A";
        }
        ZoneId zone;
        try {
            zone = timezone == null || timezone.isBlank() ? ZoneId.of("Asia/Kolkata") : ZoneId.of(timezone);
        } catch (RuntimeException invalidZone) {
            zone = ZoneId.of("Asia/Kolkata");
        }
        return start.atZoneSameInstant(zone).format(WHEN);
    }

    public static String formatStart(OffsetDateTime start, Spa spa) {
        if (start == null) {
            return "N/A";
        }
        if (spa != null && spa.getTimezone() != null && !spa.getTimezone().isBlank()) {
            try {
                return start.atZoneSameInstant(ZoneId.of(spa.getTimezone())).format(WHEN);
            } catch (RuntimeException invalidZone) {
                // fall through to the stored offset
            }
        }
        return start.format(WHEN);
    }

    private static String greeting(Booking b) {
        User user = b.getUser();
        String name = user != null ? user.getName() : null;
        return "<p>Hello" + (name == null || name.isBlank() ? "" : " " + esc(name)) + ",</p>";
    }

    private static String summary(Booking b, boolean forSpa) {
        Spa spa = b.getSpa();
        User user = b.getUser();
        StringBuilder html = new StringBuilder("<ul>")
                .append(line("Reference", b.getBookingReference()))
                .append(forSpa
                        ? line("Guest", user != null ? user.getName() : null)
                        : line("Spa", spa != null ? spa.getName() : null))
                .append(line("Service", b.getService() != null ? b.getService().getName() : null))
                .append(line("When", formatStart(b.getStartTs(), spa)));
        if (b.getGuestCount() != null && b.getGuestCount() > 1) {
            html.append(line("Guests", String.valueOf(b.getGuestCount())));
        }
        return html.append("</ul>").toString();
    }

    private static boolean bySpa(Booking b) {
        return b.getCancelledBy() == com.nirvana.application.model.enums.CancellationActor.SPA
                || b.getCancelledBy() == com.nirvana.application.model.enums.CancellationActor.ADMIN;
    }

    private static boolean payAtSpa(Booking b) {
        return b.getPaymentMode() == com.nirvana.application.model.enums.PaymentMode.OFFLINE
                && b.getPaymentType() != com.nirvana.application.model.enums.BookingPaymentType.PAID_BY_PACKAGE;
    }

    private static String line(String label, String value) {
        return "<li><strong>" + label + ":</strong> " + esc(value) + "</li>";
    }

    private static String esc(String value) {
        return value == null || value.isBlank() ? "N/A" : HtmlUtils.htmlEscape(value);
    }
}
