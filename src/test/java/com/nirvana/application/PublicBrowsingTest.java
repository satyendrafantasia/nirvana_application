package com.nirvana.application;

import com.nirvana.application.model.enums.BookingStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Guests browse spas without an account; anything personal, paid or unpublished still needs more. */
@SpringBootTest @AutoConfigureMockMvc @Transactional
class PublicBrowsingTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    private long spa(boolean published) {
        long id = jdbc.queryForObject("SELECT MIN(id) FROM spa WHERE is_active=1 AND is_verified=1", Long.class);
        if (!published) {
            id = jdbc.queryForObject("SELECT MAX(id) FROM spa WHERE is_active=1 AND is_verified=1", Long.class);
            jdbc.update("UPDATE spa SET is_verified=0 WHERE id=?", id);
        }
        return id;
    }

    @Test void guestsCanSearchAndOpenPublishedSpas() throws Exception {
        long id = spa(true);
        mvc.perform(get("/api/spas/search").param("page", "0").param("size", "1")).andExpect(status().isOk());
        mvc.perform(get("/api/spas/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/spas/" + id + "/services")).andExpect(status().isOk());
        mvc.perform(get("/api/spas/" + id + "/reviews")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/spa/" + id + "/packages/available")).andExpect(status().isOk());
        mvc.perform(get("/api/packages/catalog")).andExpect(status().isOk());
        mvc.perform(get("/api/faqs")).andExpect(status().isOk());
    }

    @Test void publicReviewsDoNotSayWhoWroteThem() throws Exception {
        var booking = BookingFixtures.payAtSpa(em, OffsetDateTime.now(ZoneOffset.UTC).minusDays(1), BookingStatus.COMPLETED);
        long id = booking.getSpa().getId();
        jdbc.update("UPDATE spa SET is_verified=1, is_active=1 WHERE id=?", id);
        // Only this review should be visible on the first page, regardless of the seed data.
        jdbc.update("UPDATE review SET is_visible=0 WHERE spa_id=?", id);
        jdbc.update("""
                INSERT INTO review (created_at, booking_id, spa_id, user_id, rating, title, text, is_visible, helpful_count, reported_count)
                VALUES (UTC_TIMESTAMP(), ?, ?, ?, 5, 'Lovely', 'Great massage.', 1, 0, 0)""",
                booking.getId(), id, booking.getUser().getId());
        mvc.perform(get("/api/spas/" + id + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.content[0].text").value("Great massage."))
                .andExpect(jsonPath("$.page.content[0].userId").doesNotExist())
                .andExpect(jsonPath("$.page.content[0].bookingId").doesNotExist());
    }

    @Test void unpublishedSpasStayHidden() throws Exception {
        long id = spa(false);
        mvc.perform(get("/api/spas/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/spas/" + id + "/services")).andExpect(status().isNotFound());
        mvc.perform(get("/api/spas/" + id + "/google-photos")).andExpect(status().isNotFound());
    }

    @Test void bookingBuyingAndAccountsStillNeedSignIn() throws Exception {
        mvc.perform(post("/api/bookings").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/packages/2/checkout")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/packages/mine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/bookings")).andExpect(status().isUnauthorized());
        // The general Google photo proxy works for any place, so it is not open to guests.
        mvc.perform(get("/api/discovery/places/abc/photos/xyz/image")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/spas/1/settings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/spas/recommendations")).andExpect(status().isUnauthorized());
    }
}
