package com.nirvana.application.service;

import com.nirvana.application.model.Booking;
import com.nirvana.application.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * What the spa does with the link in its SMS: see the booking, confirm it, or decline it. The
 * link is the only credential, so it shows no more than the spa needs and stops working once the
 * appointment is over. Declining cancels the booking the way a spa cancellation always does: the
 * guest is told and gets a full refund or their package session back.
 */
@Service
@RequiredArgsConstructor
public class SpaBookingReplies {
    private final JdbcTemplate jdbc;
    private final BookingRepository bookings;
    private final BookingService bookingService;

    public record View(String reference, String spaName, String service, String when, int guests, String guestName,
                       String payment, String status, String bookingStatus) {}

    @Transactional(readOnly = true)
    public View view(String token) {
        Map<String, Object> row = row(token, false);
        Booking b = booking(row);
        String guest = Optional.ofNullable(b.getUser()).map(u -> u.getName()).filter(n -> !n.isBlank())
                .map(n -> n.strip().split("\\s+")[0]).orElse("Guest");
        String summary = SpaBookingAlerts.summary(b);
        return new View(b.getBookingReference(), b.getSpa().getName(), b.getService() != null ? b.getService().getName() : null,
                SpaBookingAlerts.when(b.getStartTs(), b), Optional.ofNullable(b.getGuestCount()).orElse(1), guest,
                summary.substring(summary.lastIndexOf(", ") + 2), (String) row.get("status"), b.getStatus().name());
    }

    @Transactional
    public View confirm(String token) {
        Map<String, Object> row = row(token, true);
        Booking b = booking(row);
        String status = (String) row.get("status");
        if ("DECLINED".equals(status) || "CLOSED".equals(status) || !bookable(b))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking has been cancelled, so it can't be confirmed.");
        jdbc.update("UPDATE spa_booking_confirmation SET status='CONFIRMED', responded_at=UTC_TIMESTAMP(6), updated_at=UTC_TIMESTAMP(6) WHERE booking_id=?",
                b.getId());
        return view(token);
    }

    @Transactional
    public View decline(String token, String reason) {
        Map<String, Object> row = row(token, true);
        Booking b = booking(row);
        if ("DECLINED".equals(row.get("status"))) return view(token);
        if (!bookable(b))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking has already been cancelled.");
        String why = reason == null || reason.isBlank() ? "The spa can't take this booking." : reason.strip();
        try {
            bookingService.cancelBookingBySpa(b.getSpa().getId(), b.getId(), "Declined by the spa: " + why, false);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
        jdbc.update("""
                UPDATE spa_booking_confirmation SET status='DECLINED', decline_reason=?, responded_at=UTC_TIMESTAMP(6), updated_at=UTC_TIMESTAMP(6)
                WHERE booking_id=?""", why, b.getId());
        return view(token);
    }

    private Map<String, Object> row(String token, boolean lock) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{16,32}")) throw notFound();
        var rows = jdbc.queryForList("SELECT booking_id, status FROM spa_booking_confirmation WHERE token=?" + (lock ? " FOR UPDATE" : ""), token);
        if (rows.isEmpty()) throw notFound();
        return rows.get(0);
    }

    private Booking booking(Map<String, Object> row) {
        Booking b = bookings.findById(((Number) row.get("booking_id")).longValue()).orElseThrow(this::notFound);
        // The link stops working once the appointment is over.
        if (b.getEndTs() != null && b.getEndTs().isBefore(OffsetDateTime.now())) throw new ResponseStatusException(HttpStatus.GONE,
                "This appointment is over, so the link no longer works.");
        return b;
    }

    private static boolean bookable(Booking b) {
        return switch (b.getStatus()) {
            case CONFIRMED, PENDING_PAYMENT -> true;
            default -> false;
        };
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "This link isn't valid. Use the link in the latest SMS from Nirvana.");
    }
}
