package com.nirvana.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Guests browse spas without an account; anything personal, paid or unpublished still needs more. */
@SpringBootTest @AutoConfigureMockMvc @Transactional
class PublicBrowsingTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

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
        var booking = jdbc.queryForMap("""
                SELECT b.id, b.spa_id, b.user_id FROM booking b JOIN spa s ON s.id=b.spa_id AND s.is_active=1 AND s.is_verified=1
                WHERE NOT EXISTS (SELECT 1 FROM review r WHERE r.booking_id=b.id) ORDER BY b.id LIMIT 1""");
        long id = ((Number) booking.get("spa_id")).longValue();
        jdbc.update("""
                INSERT INTO review (created_at, booking_id, spa_id, user_id, rating, title, text, is_visible, helpful_count, reported_count)
                VALUES (UTC_TIMESTAMP(), ?, ?, ?, 5, 'Lovely', 'Great massage.', 1, 0, 0)""",
                booking.get("id"), id, booking.get("user_id"));
        mvc.perform(get("/api/spas/" + id + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.content[?(@.title=='Lovely')].text").value("Great massage."))
                .andExpect(jsonPath("$.page.content[?(@.title=='Lovely')].userId").value(org.hamcrest.Matchers.contains((Object) null)));
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
