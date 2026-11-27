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
 * operations team follows up if the spa has not replied.
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

    /** Establish the reply for this specific appointment time inside the booking transaction. */
    public void prepare(Booking b) {
        var w = window(b);
        var phone = SpaAlertSms.mobile(b.getSpa().getPhone());
        jdbc.update("""
            INSERT INTO spa_booking_confirmation(booking_id,spa_id,token,status,phone,reminder_due_at,deadline_at,delivery_revision,created_at,updated_at)
            VALUES(?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE booking_id=booking_id
            """, b.getId(), b.getSpa().getId(), token(), phone.isPresent() ? "PENDING" : "NO_MOBILE",
                phone.orElse(null), utc(w.reminder), utc(w.deadline), b.getNotificationRevision());
        jdbc.update("""
            UPDATE spa_booking_confirmation SET token=?,status=?,phone=?,sent_at=NULL,reminder_due_at=?,deadline_at=?,
                reminder_sent_at=NULL,responded_at=NULL,decline_reason=NULL,delivery_revision=?,updated_at=UTC_TIMESTAMP(6)
            WHERE booking_id=? AND delivery_revision<?
            """, token(), phone.isPresent() ? "PENDING" : "NO_MOBILE", phone.orElse(null), utc(w.reminder), utc(w.deadline),
                b.getNotificationRevision(), b.getId(), b.getNotificationRevision());
    }

    public void bookingConfirmed(Booking b) { invite(b, SpaAlertSms.Kind.NEW); }
    public void bookingRescheduled(Booking b) { invite(b, SpaAlertSms.Kind.MOVED); }

    private void invite(Booking b, SpaAlertSms.Kind kind) {
        prepare(b);
        var row = jdbc.queryForMap("SELECT token,status,sent_at,deadline_at FROM spa_booking_confirmation WHERE booking_id=? FOR UPDATE",b.getId());
        if (row.get("sent_at") != null || List.of("CONFIRMED","DECLINED","CLOSED").contains(row.get("status"))) return;
        if (!enabled) throw new BookingContactUnavailable("Spa mobile alerts are disabled");
        var phone = SpaAlertSms.mobile(b.getSpa().getPhone());
        if (phone.isEmpty()) throw new BookingContactUnavailable("Spa mobile number is unavailable");
        String token = (String)row.get("token");
        var deadlineAt=row.get("deadline_at");
        if (deadlineAt == null) {
            Window w=window(b);
            jdbc.update("UPDATE spa_booking_confirmation SET deadline_at=?,reminder_due_at=? WHERE booking_id=?",utc(w.deadline),utc(w.reminder),b.getId());
            deadlineAt=utc(w.deadline);
        }
        String deadline = time(fromUtc(deadlineAt), b);
        String prefix = kind == SpaAlertSms.Kind.MOVED ? " has moved: " : ": ";
        deliver(phone.get(), kind, "Nirvana booking " + b.getBookingReference() + prefix + summary(b)
                + ". Please confirm or decline: " + link(token), List.of(b.getBookingReference(), summary(b), deadline), token, b);
        jdbc.update("UPDATE spa_booking_confirmation SET status='PENDING',phone=?,sent_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6) WHERE booking_id=?", phone.get(), b.getId());
    }

    /** The guest (or Nirvana) cancelled: the spa is told, unless it was the spa that declined. */
    public void bookingCancelled(Booking b) {
        jdbc.update("UPDATE spa_booking_confirmation SET status='CLOSED', updated_at=UTC_TIMESTAMP(6) WHERE booking_id=? AND status IN ('PENDING','CONFIRMED','NEEDS_CONTACT','NO_MOBILE')",
                b.getId());
        if (b.getCancelledBy() == CancellationActor.SPA) return;
        var phone = jdbc.queryForList("SELECT phone FROM spa_booking_confirmation WHERE booking_id=? AND phone IS NOT NULL AND status='CLOSED'",
                String.class, b.getId());
        if (!enabled) throw new BookingContactUnavailable("Spa mobile alerts are disabled");
        if (phone.isEmpty()) phone=SpaAlertSms.mobile(b.getSpa().getPhone()).stream().toList();
        if (phone.isEmpty()) throw new BookingContactUnavailable("Spa mobile number is unavailable");
        deliver(phone.get(0), SpaAlertSms.Kind.CANCELLED, "Nirvana booking " + b.getBookingReference() + " for "
                + when(b.getStartTs(), b) + " has been cancelled. The time is free again. No action needed.",
                List.of(b.getBookingReference(), when(b.getStartTs(), b)), null, b);
    }

    @org.springframework.transaction.annotation.Transactional
    public int runDue() {
        int count = jdbc.update("""
            INSERT INTO booking_alert_job(booking_id,event_kind,audience,revision)
            SELECT c.booking_id,'REMINDER','SPA_MOBILE',c.delivery_revision
            FROM spa_booking_confirmation c JOIN booking b ON b.id=c.booking_id
            WHERE c.status='PENDING' AND c.sent_at IS NOT NULL AND c.reminder_sent_at IS NULL
              AND c.reminder_due_at<=UTC_TIMESTAMP() AND c.deadline_at>UTC_TIMESTAMP()
              AND b.status='CONFIRMED' AND b.end_ts>UTC_TIMESTAMP()
            ON DUPLICATE KEY UPDATE id=booking_alert_job.id
            """);
        // An unanswered appointment still stands. Staff must contact the spa; only its reply acknowledges it.
        count += jdbc.update("""
            UPDATE spa_booking_confirmation c JOIN booking b ON b.id=c.booking_id
            SET c.status='NEEDS_CONTACT',c.updated_at=UTC_TIMESTAMP(6)
            WHERE c.status='PENDING' AND c.deadline_at<=UTC_TIMESTAMP() AND b.status='CONFIRMED'
            """);
        jdbc.update("""
            INSERT INTO booking_alert_job(booking_id,event_kind,audience,revision,state)
            SELECT c.booking_id,'FOLLOW_UP','SPA_MOBILE',c.delivery_revision,'NEEDS_CONTACT'
            FROM spa_booking_confirmation c JOIN booking b ON b.id=c.booking_id
            WHERE c.status='NEEDS_CONTACT' AND b.status='CONFIRMED' AND b.end_ts>UTC_TIMESTAMP()
            ON DUPLICATE KEY UPDATE id=booking_alert_job.id
            """);
        jdbc.update("""
            UPDATE booking_alert_job j JOIN booking b ON b.id=j.booking_id
            LEFT JOIN spa_booking_confirmation c ON c.booking_id=b.id
            SET j.state='OBSOLETE'
            WHERE j.state IN ('NEEDS_CONTACT','FAILED') AND j.event_kind IN ('CONFIRMED','RESCHEDULED','FOLLOW_UP','REMINDER')
              AND (j.revision<>b.notification_revision OR b.status NOT IN ('CONFIRMED','PENDING_PAYMENT','ARRIVED')
                   OR b.end_ts<=UTC_TIMESTAMP() OR (j.audience IN ('SPA_EMAIL','SPA_MOBILE','SPA_PUSH') AND c.status='CONFIRMED'))
            """);
        jdbc.update("""
            UPDATE booking_alert_job j JOIN appointment_request r ON r.id=j.request_id SET j.state='OBSOLETE'
            WHERE j.event_kind='REQUEST_NEW' AND j.state IN ('NEEDS_CONTACT','FAILED') AND (r.state<>'PENDING' OR r.deadline<=UTC_TIMESTAMP())
            """);
        return count;
    }

    public boolean remind(Booking b) {
        var rows = jdbc.queryForList("""
            SELECT token FROM spa_booking_confirmation WHERE booking_id=? AND delivery_revision=? AND status='PENDING'
              AND reminder_sent_at IS NULL AND sent_at IS NOT NULL AND deadline_at>UTC_TIMESTAMP() FOR UPDATE
            """, b.getId(), b.getNotificationRevision());
        if (rows.isEmpty()) return false;
        var phone = SpaAlertSms.mobile(b.getSpa().getPhone());
        if (!enabled || phone.isEmpty()) throw new BookingContactUnavailable("Spa mobile reminder unavailable");
        var token = (String) rows.get(0).get("token");
        var deadline = jdbc.queryForObject("SELECT deadline_at FROM spa_booking_confirmation WHERE booking_id=?",Timestamp.class,b.getId());
        var starts = when(b.getStartTs(), b);
        var replyBy = time(fromUtc(deadline), b);
        deliver(phone.get(), SpaAlertSms.Kind.REMINDER, "Nirvana booking "+b.getBookingReference()+" on "+starts+
                " is awaiting your reply. Confirm or decline: "+link(token),List.of(b.getBookingReference(),starts,replyBy),token,b);
        jdbc.update("UPDATE spa_booking_confirmation SET reminder_sent_at=UTC_TIMESTAMP(6) WHERE booking_id=?", b.getId());
        return true;
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
     * fails, the same alert goes as an SMS. Delivery failures return to the outbox for recovery.
     */
    private void send(String to, SpaAlertSms.Kind kind, String message, List<String> values, String token, String reference) {
        if (whatsApp.enabled()) {
            try {
                whatsApp.send(to, kind, values, token);
                return;
            } catch (RuntimeException e) {
                log.warn("{} WhatsApp delivery failed for booking {}; trying SMS", kind, reference);
            }
        }
        sms.send(to, kind, message);
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

    /**
     * A stored UTC wall-clock time as an instant. Drivers hand DATETIME columns back as Timestamp
     * or as LocalDateTime depending on version and settings, so both are accepted.
     */
    public static Instant fromUtc(Object stored) {
        if (stored instanceof Timestamp t) return t.toLocalDateTime().toInstant(ZoneOffset.UTC);
        if (stored instanceof LocalDateTime t) return t.toInstant(ZoneOffset.UTC);
        if (stored instanceof OffsetDateTime t) return t.toInstant();
        throw new IllegalArgumentException("Not a stored time: " + (stored == null ? "null" : stored.getClass().getName()));
    }
}
