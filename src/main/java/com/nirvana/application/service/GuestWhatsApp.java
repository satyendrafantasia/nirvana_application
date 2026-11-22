package com.nirvana.application.service;

import com.nirvana.application.model.Address;
import com.nirvana.application.model.Booking;
import com.nirvana.application.model.User;
import com.nirvana.application.model.enums.BookingPaymentType;
import com.nirvana.application.model.enums.PaymentMode;
import com.nirvana.application.service.impl.BookingEmailContent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Booking updates for guests on WhatsApp: confirmed, reminder, new time and cancelled. Only guests
 * who agreed to WhatsApp updates and have a verified mobile get them (WhatsApp allows messages only to
 * people who opted in); everyone with an email still gets the email. Each kind has an approved
 * Utility template whose button opens My bookings.
 */
@Slf4j
@Service
public class GuestWhatsApp {
    public enum Kind { CONFIRMED, REMINDER, MOVED, CANCELLED }

    private final SpaAlertWhatsApp whatsApp;
    private final boolean enabled;
    private final String confirmed;
    private final String reminder;
    private final String moved;
    private final String cancelled;

    public GuestWhatsApp(SpaAlertWhatsApp whatsApp,
                         @Value("${guest-alerts.whatsapp.enabled:false}") boolean enabled,
                         @Value("${guest-alerts.whatsapp.templates.confirmed:nirvana_guest_booking_confirmed}") String confirmed,
                         @Value("${guest-alerts.whatsapp.templates.reminder:nirvana_guest_booking_reminder}") String reminder,
                         @Value("${guest-alerts.whatsapp.templates.moved:nirvana_guest_booking_moved}") String moved,
                         @Value("${guest-alerts.whatsapp.templates.cancelled:nirvana_guest_booking_cancelled}") String cancelled) {
        this.whatsApp = whatsApp;
        this.enabled = enabled;
        this.confirmed = confirmed;
        this.reminder = reminder;
        this.moved = moved;
        this.cancelled = cancelled;
    }

    /** The guest's WhatsApp number, if guest updates are on and this guest agreed to them. */
    public Optional<String> recipient(Booking b) {
        if (!enabled || !whatsApp.configured() || b == null) return Optional.empty();
        User u = b.getUser();
        if (u == null || u.getWhatsappUpdatesAt() == null || !Boolean.TRUE.equals(u.getPhoneVerified())) return Optional.empty();
        String phone = u.getPhone() == null ? "" : u.getPhone().strip();
        String digits = phone.replaceAll("\\D", "");
        return phone.startsWith("+") && digits.length() >= 8 && digits.length() <= 15 ? Optional.of("+" + digits) : Optional.empty();
    }

    /**
     * Sends the update if this guest gets WhatsApp updates. Returns false when they don't; throws when
     * WhatsApp refuses it, so the caller can retry.
     */
    public boolean send(Booking b, Kind kind) {
        Optional<String> to = recipient(b);
        if (to.isEmpty()) return false;
        whatsApp.sendTemplate(to.get(), template(kind), values(b, kind), null, "guest " + kind);
        return true;
    }

    String template(Kind kind) {
        return switch (kind) {
            case CONFIRMED -> confirmed;
            case REMINDER -> reminder;
            case MOVED -> moved;
            case CANCELLED -> cancelled;
        };
    }

    /** The template values, in the order the template texts use them. */
    static List<String> values(Booking b, Kind kind) {
        String ref = b.getBookingReference();
        String spa = b.getSpa() != null ? b.getSpa().getName() : "the spa";
        String treatment = b.getService() != null ? b.getService().getName() : "Your appointment";
        String when = BookingEmailContent.formatStart(b.getStartTs(), b.getSpa());
        return switch (kind) {
            // Your Nirvana booking {{1}} is confirmed: {{2}} at {{3}} on {{4}}. {{5}} See you there!
            case CONFIRMED -> List.of(ref, treatment, spa, when, payment(b));
            // Reminder from Nirvana: your {{1}} at {{2}} is on {{3}}. Address: {{4}}. {{5}} Booking ref {{6}}. See you soon!
            case REMINDER -> List.of(treatment, spa, when, address(b), payment(b), ref);
            // Your Nirvana booking {{1}} at {{2}} has moved to {{3}}. You can view or change it in My bookings.
            case MOVED -> List.of(ref, spa, when);
            // Your Nirvana booking {{1}} at {{2}} on {{3}} has been cancelled. Details are in My bookings.
            case CANCELLED -> List.of(ref, spa, when);
        };
    }

    /** "Please pay Rs 1,999 at the spa." / "Paid online." / "Paid with your Nirvana package." */
    static String payment(Booking b) {
        if (b.getPaymentType() == BookingPaymentType.PAID_BY_PACKAGE) return "Paid with your Nirvana package.";
        if (b.getPaymentMode() == PaymentMode.OFFLINE) {
            int paise = Optional.ofNullable(b.getRemainderCents()).orElse(0);
            if (paise > 0) return "Please pay Rs " + NumberFormat.getIntegerInstance(new Locale("en", "IN"))
                    .format(Math.round(paise / 100.0)) + " at the spa.";
            return "Payment is due at the spa.";
        }
        return "Paid online.";
    }

    static String address(Booking b) {
        Address a = b.getSpa() != null ? b.getSpa().getAddress() : null;
        if (a == null) return "see My bookings";
        String joined = String.join(", ", Stream.of(a.getAddressLine(), a.getLocality(), a.getCity())
                .filter(StringUtils::hasText).map(String::strip).distinct().toList());
        return joined.isEmpty() ? "see My bookings" : joined;
    }
}
