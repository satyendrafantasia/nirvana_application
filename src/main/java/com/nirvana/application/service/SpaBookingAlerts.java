package com.nirvana.application.service;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.enums.BookingPaymentType;
import com.nirvana.application.model.enums.CancellationActor;
import com.nirvana.application.model.enums.PaymentMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Tells the spa about each booking on WhatsApp (when set up) or by SMS, with a private link to
 * confirm or decline it. Most
 * spas listed from Google Maps have no Nirvana account or email, but most have a mobile number.
 * The guest's booking stands meanwhile. A spa that hasn't replied gets one last reminder, and the
 * booking is then confirmed for it. Nothing here ever fails the booking: a lost SMS is logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpaBookingAlerts {
    static final Duration REPLY_WINDOW = Duration.ofHours(2);
    static final Duration REMINDER_AFTER = Duration.ofMinutes(30);
    /** The spa's answer is wanted at least this long before the appointment. */
    static final Duration BEFORE_START = Duration.ofHours(1);
    static final Duration SHORTEST_WINDOW = Duration.ofMinutes(20);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final SpaAlertSms sms;
    private final SpaAlertWhatsApp whatsApp;

    @Value("${spa-alerts.enabled:true}")
    private boolean enabled;
    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    /** A new booking (pay at spa, package, or paid online once the payment is in). */
    public void bookingConfirmed(Booking b) {
        if (!enabled || jdbc.queryForObject("SELECT COUNT(*) FROM spa_booking_confirmation WHERE booking_id=?", Integer.class, b.getId()) > 0)
            return;
        Optional<String> to = SpaAlertSms.mobile(b.getSpa().getPhone());
        String token = token();
        Window w = window(b);
        jdbc.update("""
                INSERT INTO spa_booking_confirmation (booking_id, spa_id, token, status, phone, sent_at, reminder_due_at, deadline_at, created_at, updated_at)
                VALUES (?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))""",
                b.getId(), b.getSpa().getId(), token, to.isPresent() ? "PENDING" : "NO_MOBILE", to.orElse(null),
                to.isPresent() ? utc(Instant.now()) : null, to.isPresent() ? utc(w.reminder) : null, to.isPresent() ? utc(w.deadline) : null);
        if (to.isEmpty()) {
            log.warn("Spa {} has no mobile number for SMS; booking {} relies on email or a call", b.getSpa().getId(), b.getBookingReference());
            return;
        }
        deliver(to.get(), SpaAlertSms.Kind.NEW, "Nirvana booking " + b.getBookingReference() + ": " + summary(b)
                + ". Please confirm or decline: " + link(token), List.of(b.getBookingReference(), summary(b), time(w.deadline, b)), token, b);
    }

    /** A new time: the spa is asked again, with a fresh link. */
    public void bookingRescheduled(Booking b) {
        if (!enabled) return;
        Optional<String> to = SpaAlertSms.mobile(b.getSpa().getPhone());
        if (to.isEmpty()) return;
        String token = token();
        Window w = window(b);
        int updated = jdbc.update("""
                UPDATE spa_booking_confirmation SET token=?, status='PENDING', phone=?, sent_at=UTC_TIMESTAMP(6), reminder_due_at=?,
                       reminder_sent_at=NULL, deadline_at=?, responded_at=NULL, updated_at=UTC_TIMESTAMP(6)
                WHERE booking_id=? AND status<>'DECLINED'""", token, to.get(), utc(w.reminder), utc(w.deadline), b.getId());
        if (updated == 0) {
            bookingConfirmed(b);
            return;
        }
        deliver(to.get(), SpaAlertSms.Kind.MOVED, "Nirvana booking " + b.getBookingReference() + " has moved: " + summary(b)
                + ". Please confirm or decline: " + link(token), List.of(b.getBookingReference(), summary(b), time(w.deadline, b)), token, b);
    }

    /** The guest (or Nirvana) cancelled: the spa is told, unless it was the spa that declined. */
    public void bookingCancelled(Booking b) {
        if (!enabled) return;
        jdbc.update("UPDATE spa_booking_confirmation SET status='CLOSED', updated_at=UTC_TIMESTAMP(6) WHERE booking_id=? AND status IN ('PENDING','CONFIRMED','AUTO_CONFIRMED')",
                b.getId());
        if (b.getCancelledBy() == CancellationActor.SPA) return;
        var phone = jdbc.queryForList("SELECT phone FROM spa_booking_confirmation WHERE booking_id=? AND phone IS NOT NULL AND status='CLOSED'",
                String.class, b.getId());
        if (phone.isEmpty()) return;
        deliver(phone.get(0), SpaAlertSms.Kind.CANCELLED, "Nirvana booking " + b.getBookingReference() + " for "
                + when(b.getStartTs(), b) + " has been cancelled. The time is free again. No action needed.",
                List.of(b.getBookingReference(), when(b.getStartTs(), b)), null, b);
    }

    /**
     * Sends the one last reminder that is due, then confirms bookings whose spa still hasn't
     * replied. Each row is claimed before its SMS is sent, so instances never double up.
     */
    public int runDue() {
        if (!enabled) return 0;
        int done = 0;
        for (var row : jdbc.queryForList("""
                SELECT c.booking_id, c.token, c.phone, c.deadline_at, b.booking_reference, b.start_ts, s.timezone
                FROM spa_booking_confirmation c JOIN booking b ON b.id=c.booking_id JOIN spa s ON s.id=c.spa_id
                WHERE c.status='PENDING' AND c.reminder_sent_at IS NULL AND c.reminder_due_at <= UTC_TIMESTAMP(6)
                ORDER BY c.reminder_due_at LIMIT 500""")) {
            long id = ((Number) row.get("booking_id")).longValue();
            if (jdbc.update("UPDATE spa_booking_confirmation SET reminder_sent_at=UTC_TIMESTAMP(6), updated_at=UTC_TIMESTAMP(6) WHERE booking_id=? AND status='PENDING' AND reminder_sent_at IS NULL", id) == 0)
                continue;
            // Same link as the first SMS, so either one works.
            String token = (String) row.get("token");
            ZoneId zone = zone((String) row.get("timezone"));
            String deadline = fromUtc((Timestamp) row.get("deadline_at")).atZone(zone).format(TIME);
            OffsetDateTime start = fromUtc((Timestamp) row.get("start_ts")).atOffset(ZoneOffset.UTC);
            String reference = (String) row.get("booking_reference");
            String startsAt = start.atZoneSameInstant(zone).format(WHEN);
            send((String) row.get("phone"), SpaAlertSms.Kind.REMINDER, "Last reminder: Nirvana booking " + reference
                    + " on " + startsAt + " is waiting for you. Confirm or decline by " + deadline
                    + ": " + link(token) + " If we don't hear back, it stays booked.", List.of(reference, startsAt, deadline), token, reference);
            done++;
        }
        // One last reminder has gone and the time is up: the booking stands, confirmed for the spa.
        done += jdbc.update("""
                UPDATE spa_booking_confirmation SET status='AUTO_CONFIRMED', responded_at=UTC_TIMESTAMP(6), updated_at=UTC_TIMESTAMP(6)
                WHERE status='PENDING' AND reminder_sent_at IS NOT NULL AND deadline_at <= UTC_TIMESTAMP(6)""");
        return done;
    }

    record Window(Instant reminder, Instant deadline) {}

    /** Answer within two hours, or an hour before the start if sooner; the reminder comes halfway, at most 30 minutes in. */
    static Window window(Instant now, Instant start) {
        Instant deadline = Collections.min(List.of(now.plus(REPLY_WINDOW), start.minus(BEFORE_START)));
        if (deadline.isBefore(now.plus(SHORTEST_WINDOW))) deadline = now.plus(SHORTEST_WINDOW);
        Duration half = Duration.between(now, deadline).dividedBy(2);
        return new Window(now.plus(half.compareTo(REMINDER_AFTER) < 0 ? half : REMINDER_AFTER), deadline);
    }

    private static Window window(Booking b) {
        return window(Instant.now(), b.getStartTs().toInstant());
    }

    /** "Swedish Massage, Tue 25 Nov, 7:00 PM, 1 guest, guest pays Rs 1,999 at the spa" */
    static String summary(Booking b) {
        int guests = Optional.ofNullable(b.getGuestCount()).orElse(1);
        String pay = b.getPaymentType() == BookingPaymentType.PAID_BY_PACKAGE ? "paid with a Nirvana package"
                : b.getPaymentMode() == PaymentMode.OFFLINE ? "guest pays Rs " + rupees(Optional.ofNullable(b.getRemainderCents()).orElse(0)) + " at the spa"
                : "paid online";
        return (b.getService() != null ? b.getService().getName() + ", " : "") + when(b.getStartTs(), b)
                + ", " + guests + (guests == 1 ? " guest, " : " guests, ") + pay;
    }

    static String when(OffsetDateTime start, Booking b) {
        return start.atZoneSameInstant(zone(b.getSpa().getTimezone())).format(WHEN);
    }

    private static String rupees(int paise) {
        NumberFormat f = NumberFormat.getIntegerInstance(new Locale("en", "IN"));
        return f.format(Math.round(paise / 100.0));
    }

    private void deliver(String to, SpaAlertSms.Kind kind, String message, List<String> values, String token, Booking b) {
        send(to, kind, message, values, token, b.getBookingReference());
    }

    /**
     * WhatsApp first when it is set up, using the approved template for this kind of alert; if that
     * fails, the same alert goes as an SMS. A lost alert is logged, never thrown.
     */
    private void send(String to, SpaAlertSms.Kind kind, String message, List<String> values, String token, String reference) {
        if (whatsApp.enabled()) {
            try {
                whatsApp.send(to, kind, values, token);
                return;
            } catch (RuntimeException e) {
                log.warn("{} WhatsApp to the spa for booking {} failed, trying SMS: {}", kind, reference, e.getMessage());
            }
        }
        try {
            sms.send(to, kind, message);
        } catch (RuntimeException e) {
            log.warn("{} SMS to the spa for booking {} failed: {}", kind, reference, e.getMessage());
        }
    }

    private static String time(Instant at, Booking b) {
        return at.atZone(zone(b.getSpa().getTimezone())).format(TIME);
    }

    private String link(String token) {
        return frontendUrl.replaceAll("/+$", "") + "/b/" + token;
    }

    static ZoneId zone(String id) {
        try {
            return id == null || id.isBlank() ? ZoneId.of("Asia/Kolkata") : ZoneId.of(id);
        } catch (RuntimeException e) {
            return ZoneId.of("Asia/Kolkata");
        }
    }

    /** 128 random bits, URL-safe: short enough to keep the SMS to two parts. */
    static String token() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }


    /** Times are stored as UTC wall-clock values. */
    static Timestamp utc(Instant at) {
        return Timestamp.valueOf(LocalDateTime.ofInstant(at, ZoneOffset.UTC));
    }

    static Instant fromUtc(Timestamp stored) {
        return stored.toLocalDateTime().toInstant(ZoneOffset.UTC);
    }
}
