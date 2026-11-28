package com.nirvana.application;

import com.nirvana.application.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/me/bookings/{id}/calendar.ics: only the booking's own guest gets the invite. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookingCalendarInviteTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired jakarta.persistence.EntityManager entities;

    long template, guest, otherGuest;

    private static UsernamePasswordAuthenticationToken as(long user) {
        var p = new UserPrincipal(user, "u" + user, "u" + user + "@example.test", "", true, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    @BeforeEach
    void fixtures() {
        template = db.queryForObject("SELECT MIN(id) FROM booking", Long.class);
        var users = db.queryForList("SELECT id FROM app_user ORDER BY id LIMIT 2", Long.class);
        guest = users.get(0);
        otherGuest = users.get(1);
    }

    private long booking(long user, String status, LocalDateTime start, int revision) {
        var row = new HashMap<>(db.queryForMap("SELECT * FROM booking WHERE id=?", template));
        row.remove("id");
        row.put("booking_reference", "CAL-" + UUID.randomUUID());
        row.put("user_id", user);
        row.put("status", status);
        row.put("start_ts", start);
        row.put("end_ts", start.plusMinutes(90));
        row.put("notification_revision", revision);
        row.keySet().removeIf(k -> k.toLowerCase().contains("idempotency"));
        return new SimpleJdbcInsert(db).withTableName("booking").usingGeneratedKeyColumns("id")
                .usingColumns(row.keySet().toArray(String[]::new)).executeAndReturnKey(row).longValue();
    }

    private String fetch(long booking, long user) throws Exception {
        var response = mvc.perform(get("/api/me/bookings/" + booking + "/calendar.ics").with(authentication(as(user))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse();
        assertTrue(response.getContentType().toLowerCase().replace(" ", "").startsWith("text/calendar;charset=utf-8"), response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").startsWith("attachment; filename=\"nirvana-booking-CAL-"), response.getHeader("Content-Disposition"));
        return new String(response.getContentAsByteArray(), StandardCharsets.UTF_8).replace("\r\n ", "");
    }

    private String reference(long booking) {
        return db.queryForObject("SELECT booking_reference FROM booking WHERE id=?", String.class, booking).replaceAll("[^A-Za-z0-9._-]", "");
    }

    @Test
    void guestDownloadsTheirInvite() throws Exception {
        var start = LocalDateTime.of(2027, 3, 4, 5, 30);
        long b = booking(guest, "CONFIRMED", start, 0);
        String ics = fetch(b, guest);
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"), ics);
        assertTrue(ics.contains("\r\nMETHOD:PUBLISH\r\n"), ics);
        assertTrue(ics.contains("\r\nUID:booking-" + reference(b) + "@"), ics);
        assertTrue(ics.contains("\r\nSEQUENCE:0\r\n"), ics);
        assertTrue(ics.contains("\r\nDTSTART:20270304T053000Z\r\n"), ics);
        assertTrue(ics.contains("\r\nDTEND:20270304T070000Z\r\n"), ics);
        assertTrue(ics.contains("\r\nSTATUS:CONFIRMED\r\n"), ics);
        assertTrue(ics.contains("\r\nTRIGGER:-PT2H\r\n"), ics);
        assertTrue(ics.contains("/me/bookings"), ics);
        assertTrue(ics.contains("Booking reference: CAL-"), ics);
        assertTrue(ics.contains("\r\nSUMMARY:") && ics.contains("\r\nLOCATION:"), ics);
    }

    @Test
    void rescheduledInviteKeepsTheUidWithAHigherSequence() throws Exception {
        long b = booking(guest, "CONFIRMED", LocalDateTime.of(2027, 3, 4, 5, 30), 0);
        String before = fetch(b, guest);
        db.update("UPDATE booking SET notification_revision=notification_revision+1, start_ts=?, end_ts=? WHERE id=?",
                LocalDateTime.of(2027, 3, 5, 9, 0), LocalDateTime.of(2027, 3, 5, 10, 0), b);
        entities.clear(); // the test transaction would otherwise keep serving the booking it loaded first
        String after = fetch(b, guest);
        String uid = "\r\nUID:booking-" + reference(b) + "@";
        assertTrue(before.contains(uid) && after.contains(uid));
        assertTrue(after.contains("\r\nSEQUENCE:1\r\n"), after);
        assertTrue(after.contains("\r\nDTSTART:20270305T090000Z\r\n"), after);
    }

    @Test
    void cancelledBookingReturnsACancellation() throws Exception {
        long b = booking(guest, "CANCELLED", LocalDateTime.of(2027, 3, 4, 5, 30), 1);
        String ics = fetch(b, guest);
        assertTrue(ics.contains("\r\nMETHOD:CANCEL\r\n"), ics);
        assertTrue(ics.contains("\r\nSTATUS:CANCELLED\r\n"), ics);
        assertTrue(ics.contains("\r\nSEQUENCE:2\r\n"), ics);
        assertFalse(ics.contains("VALARM"), ics);
    }

    @Test
    void onlyTheBookingsGuestCanFetchIt() throws Exception {
        long b = booking(guest, "CONFIRMED", LocalDateTime.now(ZoneOffset.UTC).plusDays(2), 0);
        mvc.perform(get("/api/me/bookings/" + b + "/calendar.ics").with(authentication(as(otherGuest))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/me/bookings/" + b + "/calendar.ics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/bookings/99999999/calendar.ics").with(authentication(as(guest))))
                .andExpect(status().isNotFound());
    }
}
