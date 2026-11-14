package com.nirvana.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Real sign-in with real tokens, so the session rules are tested end to end. */
@SpringBootTest @AutoConfigureMockMvc @Transactional
class AccountTest {
    static final String PASSWORD = "Original-pass-1";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;

    record Reply(int status, JsonNode body) {}

    @BeforeEach void givePasswordToSeededCustomer() {
        jdbc.update("update app_user set password=?, is_active=1, credentials_changed_at=null, loyalty_points=120, timezone='Asia/Kolkata' where username='customer'",
                "{bcrypt}" + new BCryptPasswordEncoder().encode(PASSWORD));
    }

    Reply send(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType("application/json").content(json.writeValueAsString(body));
        var r = mvc.perform(req).andReturn().getResponse();
        String text = r.getContentAsString();
        return new Reply(r.getStatus(), text.isBlank() ? json.createObjectNode() : json.readTree(text));
    }
    JsonNode login(String password) throws Exception {
        var r = send(post("/api/auth/login"), null, Map.of("username", "customer", "password", password));
        assertEquals(200, r.status(), () -> r.body().toString());
        return r.body();
    }
    Map<String, Object> profile(String first, String zone) {
        return new java.util.HashMap<>(Map.of("firstName", first, "lastName", "Rao", "timezone", zone,
                "locale", "en-IN", "marketingOptIn", false));
    }

    @Test void accountShowsProfileAndPointsAndNeedsSignIn() throws Exception {
        assertEquals(401, send(get("/api/me/account"), null, null).status());
        String token = login(PASSWORD).get("accessToken").asText();
        var me = send(get("/api/me/account"), token, null);
        assertEquals(200, me.status(), () -> me.body().toString());
        assertEquals(120, me.body().get("loyaltyPoints").asInt());
        assertEquals("customer@nirvana.test", me.body().get("email").asText());
        assertFalse(me.body().has("password"));
    }

    @Test void profileIsSavedAndBadValuesAreRefused() throws Exception {
        String token = login(PASSWORD).get("accessToken").asText();
        var saved = send(put("/api/me/account"), token, profile("  Asha ", "Europe/London"));
        assertEquals(200, saved.status(), () -> saved.body().toString());
        assertEquals("Asha", saved.body().get("firstName").asText());
        assertEquals("Europe/London", saved.body().get("timezone").asText());
        assertFalse(saved.body().get("marketingOptIn").asBoolean());
        em.flush();
        assertEquals("Asha", jdbc.queryForObject("select name from app_user where username='customer'", String.class));

        assertEquals(400, send(put("/api/me/account"), token, profile(" ", "Europe/London")).status(), "first name required");
        assertEquals(400, send(put("/api/me/account"), token, profile("Asha", "Mars/Olympus")).status(), "unknown time zone");
        var badLocale = profile("Asha", "Europe/London");
        badLocale.put("locale", "<script>");
        assertEquals(400, send(put("/api/me/account"), token, badLocale).status());
        // Email cannot be changed through this endpoint even if sent.
        var sneaky = profile("Asha", "Europe/London");
        sneaky.put("email", "someone-else@example.test");
        send(put("/api/me/account"), token, sneaky);
        em.flush();
        assertEquals("customer@nirvana.test", jdbc.queryForObject("select email from app_user where username='customer'", String.class));
    }

    @Test void passwordChangeNeedsTheCurrentPasswordAndAValidNewOne() throws Exception {
        String token = login(PASSWORD).get("accessToken").asText();
        var wrong = send(post("/api/me/account/password"), token, Map.of("currentPassword", "nope-nope", "newPassword", "Brand-new-pass-2"));
        assertEquals(400, wrong.status());
        assertTrue(wrong.body().get("message").asText().contains("incorrect"));
        assertEquals(400, send(post("/api/me/account/password"), token, Map.of("currentPassword", PASSWORD, "newPassword", "short")).status());
        assertEquals(400, send(post("/api/me/account/password"), token, Map.of("currentPassword", PASSWORD, "newPassword", "x".repeat(73))).status());
        assertEquals(400, send(post("/api/me/account/password"), token, Map.of("currentPassword", PASSWORD, "newPassword", PASSWORD)).status());
        assertEquals(200, send(get("/api/me/account"), token, null).status(), "failed attempts leave the session alone");
    }

    @Test void passwordChangeEndsOtherSessionsAndKeepsThisDeviceSignedIn() throws Exception {
        var otherDevice = login(PASSWORD);
        var thisDevice = login(PASSWORD);
        Thread.sleep(1100); // tokens carry whole-second issue times; make the change land in a later second

        var changed = send(post("/api/me/account/password"), thisDevice.get("accessToken").asText(),
                Map.of("currentPassword", PASSWORD, "newPassword", "Brand-new-pass-2"));
        assertEquals(200, changed.status(), () -> changed.body().toString());
        String freshToken = changed.body().get("accessToken").asText();
        assertFalse(freshToken.isBlank());
        assertFalse(changed.body().get("refreshToken").asText().isBlank());

        assertEquals(401, send(get("/api/me/account"), otherDevice.get("accessToken").asText(), null).status(), "old access token refused");
        assertEquals(401, send(get("/api/me/account"), thisDevice.get("accessToken").asText(), null).status());
        assertEquals(200, send(get("/api/me/account"), freshToken, null).status(), "the new session works");

        var refreshOld = send(post("/api/auth/refresh"), null, Map.of("refreshToken", otherDevice.get("refreshToken").asText()));
        assertNotEquals(200, refreshOld.status(), "old refresh tokens are revoked");
        var refreshNew = send(post("/api/auth/refresh"), null, Map.of("refreshToken", changed.body().get("refreshToken").asText()));
        assertEquals(200, refreshNew.status(), () -> refreshNew.body().toString());

        var oldPassword = send(post("/api/auth/login"), null, Map.of("username", "customer", "password", PASSWORD));
        assertEquals(401, oldPassword.status());
        assertEquals("Email or password is incorrect.", oldPassword.body().get("message").asText());
        login("Brand-new-pass-2");
        // An unknown account gets the same answer, so sign-in does not reveal who has an account.
        var unknown = send(post("/api/auth/login"), null, Map.of("username", "nobody-here", "password", "Whatever-123"));
        assertEquals(401, unknown.status());
        assertEquals("Email or password is incorrect.", unknown.body().get("message").asText());
    }
}
