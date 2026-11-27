package com.nirvana.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.service.erasure.AccountErasureService;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Data export and account deletion (DPDP Act 2023), through the real sign-in and the real job. */
@SpringBootTest(properties={"email.enabled=true","email.fromAddress=qa@example.test","sms.enabled=false"}) @AutoConfigureMockMvc @Transactional
class AccountErasureTest {
    static final String PASSWORD = "Original-pass-1";
    static final String EMAIL = "customer@nirvana.test";
    static final String PHONE = "+10000000004";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired AccountErasureService erasure;
    @Autowired com.nirvana.application.repository.UserRepository users;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.nirvana.application.otp.sender.EmailSender emailSender;
    long customerId;

    record Reply(int status, JsonNode body, String raw, String disposition) {}

    @BeforeEach void prepare() {
        String hash = "{bcrypt}" + new BCryptPasswordEncoder().encode(PASSWORD);
        jdbc.update("update app_user set password=?, is_active=1, active=1, credentials_changed_at=null, data_erasure_requested_at=null, "
                + "data_erased_at=null, deleted_at=null, mfa_enabled=0 where username in ('customer','manager')", hash);
        jdbc.update("update app_user set email_verified=1 where username='manager'");
        customerId = jdbc.queryForObject("select id from app_user where username='customer'", Long.class);
        // The seeded booking is in the past here; tests that need an upcoming one move it.
        jdbc.update("update booking set start_ts=now() - interval 3 day, end_ts=now() - interval 3 day + interval 1 hour, "
                + "refund_status='NONE', customer_notes='Please use the side door' where user_id=?", customerId);
    }

    Reply send(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType("application/json").content(json.writeValueAsString(body));
        var r = mvc.perform(req).andReturn().getResponse();
        String text = r.getContentAsString();
        return new Reply(r.getStatus(), text.isBlank() ? json.createObjectNode() : json.readTree(text), text,
                r.getHeader("Content-Disposition"));
    }
    Reply signIn(String username, String password) throws Exception {
        return send(post("/api/auth/login"), null, Map.of("username", username, "password", password));
    }
    String token(String username) throws Exception {
        var r = signIn(username, PASSWORD);
        assertEquals(200, r.status());
        if (r.body().path("mfaRequired").asBoolean()) {
            var sent = org.mockito.ArgumentCaptor.forClass(String.class);
            org.mockito.Mockito.verify(emailSender).sendOtpEmail(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),sent.capture());
            var code = java.util.regex.Pattern.compile("OTP is ([0-9]{6})").matcher(sent.getValue());
            assertTrue(code.find());
            r = send(post("/api/auth/login/mfa/verify"), null, Map.of("verificationId",r.body().get("mfaVerificationId").asText(),"otp",code.group(1)));
            assertEquals(200,r.status());
        }
        return r.body().get("accessToken").asText();
    }
    Reply askToDelete(String token, Map<String, String> proof) throws Exception {
        return send(post("/api/me/account/deletion"), token, proof);
    }
    /** As if the grace period ended: the request was made more than seven days ago. */
    void graceEnds() {
        em.flush();
        jdbc.update("update app_user set data_erasure_requested_at = now() - interval 8 day where id=? and data_erasure_requested_at is not null", customerId);
        em.clear();
    }
    List<String> auditEvents() {
        return jdbc.queryForList("select event from account_erasure_audit where user_ref=? order by id", String.class, customerId);
    }

    @Test void exportHasTheirProfileBookingsReviewsAndChoicesButNoSecrets() throws Exception {
        assertEquals(401, send(get("/api/me/account/export"), null, null).status());
        jdbc.update("update app_user set privacy_consent_version='v3', marketing_opt_in=0, whatsapp_updates_at=now() where id=?", customerId);
        var r = send(get("/api/me/account/export"), token("customer"), null);
        assertEquals(200, r.status());
        assertTrue(r.disposition().startsWith("attachment; filename=\"nirvana-my-data-"), r.disposition());
        JsonNode data = r.body();
        assertEquals(EMAIL, data.at("/profile/email").asText());
        assertEquals(PHONE, data.at("/profile/phone").asText());
        assertEquals("v3", data.at("/consents/privacyPolicyVersion").asText());
        assertFalse(data.at("/notificationPreferences/offersByEmail").asBoolean());
        assertTrue(data.at("/notificationPreferences/bookingUpdatesOnWhatsapp").asBoolean());
        assertEquals(1, data.get("bookings").size());
        JsonNode booking = data.get("bookings").get(0);
        assertFalse(booking.get("reference").asText().isBlank());
        assertEquals("Please use the side door", booking.get("yourNotes").asText());
        assertTrue(booking.get("startsAt").isTextual(), "dates are ISO text: " + booking.get("startsAt"));
        assertEquals(1, data.get("reviews").size());
        for (String part : List.of("payments", "refunds", "invoices", "spaPackages", "packages", "vouchers", "favouriteSpas", "signInMethods"))
            assertTrue(data.get(part).isArray(), part);
        String raw = r.raw().toLowerCase();
        assertFalse(raw.contains("password") || raw.contains("bcrypt") || raw.contains("refresh"), "no credentials in the export");
    }

    @Test void deletionNeedsAFreshProofAndIsRefusedWhileSomethingIsOpen() throws Exception {
        String customer = token("customer");
        assertEquals(400, askToDelete(customer, Map.of()).status(), "a proof is required");
        var wrong = askToDelete(customer, Map.of("password", "not-my-password"));
        assertEquals(400, wrong.status());
        assertTrue(wrong.body().get("message").asText().contains("incorrect"));

        jdbc.update("update booking set start_ts=now() + interval 1 day, end_ts=now() + interval 1 day + interval 1 hour, status='CONFIRMED' where user_id=?", customerId);
        var upcoming = askToDelete(customer, Map.of("password", PASSWORD));
        assertEquals(409, upcoming.status(), upcoming::raw);
        assertTrue(upcoming.body().get("message").asText().contains("upcoming booking"), upcoming::raw);
        var status = send(get("/api/me/account/deletion"), customer, null);
        assertEquals(200, status.status());
        assertFalse(status.body().get("pending").asBoolean());
        assertEquals(1, status.body().get("blockers").size(), "the settings page shows why before they try");

        jdbc.update("update booking set start_ts=now() - interval 3 day, end_ts=now() - interval 3 day, refund_status='PROCESSING' where user_id=?", customerId);
        var refund = askToDelete(customer, Map.of("password", PASSWORD));
        assertEquals(409, refund.status());
        assertTrue(refund.body().get("message").asText().contains("refund"), refund::raw);

        jdbc.update("update spa set is_active=1 where spa_manager_id=(select id from spa_manager where user_id=(select id from app_user where username='manager'))");
        var owner = askToDelete(token("manager"), Map.of("password", PASSWORD));
        assertEquals(409, owner.status(), owner::raw);
        assertTrue(owner.body().get("message").asText().contains("Transfer each spa"), owner::raw);

        em.flush();
        assertNull(jdbc.queryForObject("select data_erasure_requested_at from app_user where id=?", Object.class, customerId));
        assertEquals(200, send(get("/api/me/account"), customer, null).status(), "a refused request leaves the session alone");
    }

    @Test void afterTheGracePeriodTheAccountIsErasedButTaxRecordsStay() throws Exception {
        String customer = token("customer");
        var otherDevice = signIn("customer", PASSWORD).body();
        Thread.sleep(1100); // tokens carry whole-second issue times
        var asked = askToDelete(customer, Map.of("password", PASSWORD));
        assertEquals(200, asked.status(), asked::raw);
        assertTrue(asked.body().get("pending").asBoolean());
        assertEquals(7, asked.body().get("graceDays").asInt());
        assertFalse(asked.body().get("eraseAfter").isNull());

        assertEquals(401, send(get("/api/me/account"), customer, null).status(), "signed out everywhere");
        assertNotEquals(200, send(post("/api/auth/refresh"), null, Map.of("refreshToken", otherDevice.get("refreshToken").asText())).status());
        em.flush();
        assertEquals(0, erasure.eraseDue(), "nothing is erased during the grace period");

        graceEnds();
        assertEquals(1, erasure.eraseDue());
        assertEquals(0, erasure.eraseDue(), "an erased account is not picked again");

        Map<String, Object> row = jdbc.queryForMap("select email, phone, name, last_name, display_name, username, profile_image_url, "
                + "meta, is_active, data_erased_at, marketing_opt_in from app_user where id=?", customerId);
        for (String field : List.of("email", "phone", "name", "last_name", "display_name", "profile_image_url", "meta"))
            assertNull(row.get(field), field);
        assertEquals("deleted-" + customerId, row.get("username"));
        assertNotNull(row.get("data_erased_at"));
        assertEquals(0, jdbc.queryForObject("select count(*) from refresh_token where user_id=?", Integer.class, customerId));
        // Kept for tax and accounting, with nothing personal left on them.
        assertEquals(1, jdbc.queryForObject("select count(*) from booking where user_id=?", Integer.class, customerId));
        assertNull(jdbc.queryForObject("select customer_notes from booking where user_id=?", String.class, customerId));
        assertEquals(1, jdbc.queryForObject("select count(*) from review where user_id=?", Integer.class, customerId), "review kept, author anonymous");

        assertEquals(List.of("REQUESTED", "ERASED"), auditEvents());
        String audit = String.join(" ", jdbc.queryForList("select concat(event, ' ', coalesce(detail,'')) from account_erasure_audit where user_ref=?", String.class, customerId));
        assertFalse(audit.contains("@") || audit.contains(PHONE) || audit.toLowerCase().contains("customer"), audit);

        var oldPassword = signIn("customer", PASSWORD);
        assertEquals(401, oldPassword.status(), "the deleted account cannot sign in");
        assertEquals(401, signIn("deleted-" + customerId, PASSWORD).status());

        // The email and phone can be used for a new account.
        // Registration refuses a contact that already has an account; these are free again.
        assertFalse(users.existsByEmail(EMAIL));
        assertFalse(users.existsByPhone(PHONE));
        assertTrue(users.findByPhone(PHONE).isEmpty(), "a sign-in code for the old number cannot reach the deleted account");
        assertEquals(1, jdbc.update("insert into app_user(created_at, updated_at, active, is_active, email, phone, username, password, "
                + "role_id, timezone, email_verified, phone_verified, mfa_enabled, marketing_opt_in, loyalty_points) "
                + "select now(), now(), 1, 1, ?, ?, ?, 'x', role_id, 'UTC', 1, 1, 0, 0, 0 from app_user where id=?",
                EMAIL, PHONE, EMAIL, customerId), "the unique email and phone columns accept a new account");
    }

    @Test void signingInDuringTheGracePeriodKeepsTheAccount() throws Exception {
        assertEquals(200, askToDelete(token("customer"), Map.of("password", PASSWORD)).status());
        em.flush();
        assertNotNull(jdbc.queryForObject("select data_erasure_requested_at from app_user where id=?", Object.class, customerId));

        Thread.sleep(1100);
        String again = token("customer");
        em.flush();
        assertNull(jdbc.queryForObject("select data_erasure_requested_at from app_user where id=?", Object.class, customerId));
        assertEquals(List.of("REQUESTED", "CANCELLED"), auditEvents());
        graceEnds();
        assertEquals(0, erasure.eraseDue());
        var status = send(get("/api/me/account/deletion"), again, null);
        assertFalse(status.body().get("pending").asBoolean());
        assertEquals(EMAIL, jdbc.queryForObject("select email from app_user where id=?", String.class, customerId));
    }

    @Test void aBookingMadeDuringTheGracePeriodHoldsTheDeletionBack() throws Exception {
        assertEquals(200, askToDelete(token("customer"), Map.of("password", PASSWORD)).status());
        graceEnds();
        jdbc.update("update booking set start_ts=now() + interval 1 day, end_ts=now() + interval 1 day + interval 1 hour, status='CONFIRMED' where user_id=?", customerId);
        assertEquals(0, erasure.eraseDue());
        assertEquals(List.of("REQUESTED", "DEFERRED"), auditEvents());
        assertEquals("upcoming_bookings", jdbc.queryForObject("select detail from account_erasure_audit where user_ref=? and event='DEFERRED'", String.class, customerId));
        assertEquals(EMAIL, jdbc.queryForObject("select email from app_user where id=?", String.class, customerId));
    }

    @Test void aSignInCodeProvesItIsThem() throws Exception {
        String customer = token("customer");
        Long otherUser = jdbc.queryForObject("select id from app_user where username='therapist'", Long.class);
        var someoneElses = askToDelete(customer, Map.of("loginToken", loginProof(otherUser)));
        assertEquals(403, someoneElses.status(), someoneElses::raw);
        var mine = askToDelete(customer, Map.of("loginToken", loginProof(customerId)));
        assertEquals(200, mine.status(), mine::raw);
        assertTrue(mine.body().get("pending").asBoolean());
    }

    /** What the sign-in code flow leaves behind once the code (or Firebase's phone proof) checks out. */
    String loginProof(long userId) {
        String token = UUID.randomUUID().toString();
        jdbc.update("insert into otp_verification(verification_id, otp_code, expires_at, attempts, max_attempts, verified, purpose, user_id, "
                + "login_token, login_token_expires_at, login_consumed, registration_consumed, password_reset_consumed, created_at, updated_at) "
                + "values (?, '000000', now() + interval 10 minute, 1, 5, 1, 'LOGIN', ?, ?, now() + interval 15 minute, 0, 0, 0, now(), now())",
                UUID.randomUUID().toString(), userId, token);
        return token;
    }
}
