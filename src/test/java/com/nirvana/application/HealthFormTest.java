package com.nirvana.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.security.UserPrincipal;
import com.nirvana.application.service.health.HealthFormCipher;
import com.nirvana.application.service.health.HealthFormService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.health-forms.encryption-key=" + HealthFormTest.KEY)
@AutoConfigureMockMvc
@Transactional
class HealthFormTest {
    /** A test-only key (base64 of 32 bytes). */
    static final String KEY = "bmlydmFuYS1oZWFsdGgtZm9ybS10ZXN0LWtleS0zMmI=";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired HealthFormService forms;
    @Autowired HealthFormCipher cipher;

    long spa, service, owner, guest, otherGuest, template;

    private static UsernamePasswordAuthenticationToken as(long user, String role) {
        var p = new UserPrincipal(user, "u" + user, "u" + user + "@example.test", "", true, List.of(new SimpleGrantedAuthority(role)));
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    @BeforeEach
    void fixtures() {
        template = db.queryForObject("SELECT MIN(b.id) FROM booking b JOIN spa s ON s.id=b.spa_id WHERE s.spa_manager_id IS NOT NULL", Long.class);
        var row = db.queryForMap("SELECT b.spa_id,b.service_id,m.user_id FROM booking b JOIN spa s ON s.id=b.spa_id JOIN spa_manager m ON m.id=s.spa_manager_id WHERE b.id=?", template);
        spa = ((Number) row.get("spa_id")).longValue();
        service = ((Number) row.get("service_id")).longValue();
        owner = ((Number) row.get("user_id")).longValue();
        var others = db.queryForList("SELECT id FROM app_user WHERE id<>? ORDER BY id LIMIT 2", Long.class, owner);
        guest = others.get(0);
        otherGuest = others.get(1);
    }

    /** A booking at the spa for {@code user}, ending {@code endsInHours} from now (negative: in the past). */
    private long booking(long user, long endsInHours, String status) {
        var row = new HashMap<>(db.queryForMap("SELECT * FROM booking WHERE id=?", template));
        row.remove("id");
        var end = LocalDateTime.now(ZoneOffset.UTC).plusHours(endsInHours);
        row.put("booking_reference", "HF-" + UUID.randomUUID());
        row.put("user_id", user);
        row.put("status", status);
        row.put("start_ts", end.minusHours(1));
        row.put("end_ts", end);
        row.keySet().removeIf(k -> k.toLowerCase().contains("idempotency"));
        return new SimpleJdbcInsert(db).withTableName("booking").usingGeneratedKeyColumns("id")
            .usingColumns(row.keySet().toArray(String[]::new)).executeAndReturnKey(row).longValue();
    }

    private Map<String, Object> answers(boolean pregnant, String medications) {
        var a = new HashMap<String, Object>();
        a.put("pregnant", pregnant);
        a.put("allergies", List.of("NUTS"));
        a.put("recentInjuryOrSurgery", false);
        a.put("skinCondition", false);
        a.put("heartCondition", true);
        a.put("medications", medications);
        a.put("pressure", "LIGHT");
        a.put("avoidAreas", "Lower back");
        return a;
    }

    private JsonNode call(MockHttpServletRequestBuilder request, UsernamePasswordAuthenticationToken who, int expected) throws Exception {
        var body = mvc.perform(request.with(authentication(who))).andExpect(status().is(expected)).andReturn().getResponse().getContentAsByteArray();
        return body.length == 0 ? null : json.readTree(body);
    }

    private JsonNode submit(long booking, long user, Map<String, Object> answers, int expected) throws Exception {
        var input = Map.of("answers", answers, "consent", true, "confirmedCurrent", false);
        return call(put("/api/me/bookings/" + booking + "/health-form").contentType("application/json").content(json.writeValueAsBytes(input)),
            as(user, "ROLE_USER"), expected);
    }

    @Test
    void guestSubmitsAndTheSpaSeesFlags() throws Exception {
        forms.replaceQuestions(spa, service, List.of("Any sunburn in the last week?"));
        long b = booking(guest, 48, "CONFIRMED");
        var form = call(get("/api/me/bookings/" + b + "/health-form"), as(guest, "ROLE_USER"), 200);
        assertEquals("NONE", form.get("status").asText());
        long question = form.at("/questions/0/id").asLong();
        var a = answers(true, "Aspirin");
        a.put("custom", List.of(Map.of("questionId", question, "answer", "No"), Map.of("questionId", 999999, "answer", "ignored")));
        var saved = submit(b, guest, a, 200);
        assertEquals("SUBMITTED", saved.get("status").asText());
        assertEquals(1, saved.at("/answers/custom").size());

        var desk = mvc.perform(get("/api/owner/spas/" + spa + "/desk/bookings/" + b + "/health-form").with(authentication(as(owner, "ROLE_SPA_OWNER"))))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsByteArray();
        var view = json.readTree(desk);
        var flags = new ArrayList<String>();
        view.get("attention").forEach(f -> flags.add(f.asText()));
        assertTrue(flags.contains("Pregnant"), flags.toString());
        assertTrue(flags.contains("High blood pressure or heart condition"));
        assertTrue(flags.contains("Allergic to nuts"));
        assertEquals("Any sunburn in the last week?", view.at("/answers/custom/0/prompt").asText());
        assertEquals("No", view.at("/answers/custom/0/answer").asText());
    }

    @Test
    void consentAndEveryYesNoAnswerAreRequired() throws Exception {
        long b = booking(guest, 48, "CONFIRMED");
        var input = Map.of("answers", answers(false, ""), "consent", false, "confirmedCurrent", false);
        call(put("/api/me/bookings/" + b + "/health-form").contentType("application/json").content(json.writeValueAsBytes(input)), as(guest, "ROLE_USER"), 400);
        var a = answers(false, "");
        a.remove("pregnant");
        submit(b, guest, a, 400);
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM booking_health_form WHERE booking_id=?", Integer.class, b));
    }

    @Test
    void onlyTheGuestAndThisSpasDeskMayReadTheForm() throws Exception {
        long b = booking(guest, 48, "CONFIRMED");
        submit(b, guest, answers(false, ""), 200);
        String desk = "/api/owner/spas/" + spa + "/desk/bookings/" + b + "/health-form";
        String mine = "/api/me/bookings/" + b + "/health-form";
        // Another guest may neither read, change nor withdraw it.
        call(get(mine), as(otherGuest, "ROLE_USER"), 403);
        submit(b, otherGuest, answers(true, ""), 403);
        call(delete(mine), as(otherGuest, "ROLE_USER"), 403);
        // Another spa's owner, a plain user, and desk staff of no spa are refused.
        call(get(desk), as(otherGuest, "ROLE_SPA_OWNER"), 403);
        call(get(desk), as(guest, "ROLE_USER"), 403);
        call(get(desk), as(otherGuest, "ROLE_SPA_STAFF"), 403);
        // The spa's own front-desk staff may read it.
        db.update("INSERT INTO spa_desk_staff(spa_id,user_id,added_by,added_at) VALUES(?,?,?,UTC_TIMESTAMP(6))", spa, otherGuest, owner);
        assertEquals("SUBMITTED", call(get(desk), as(otherGuest, "ROLE_SPA_STAFF"), 200).get("status").asText());
        // Questions are for the owner or manager only, not the desk.
        call(get("/api/owner/spas/" + spa + "/services/" + service + "/health-questions"), as(otherGuest, "ROLE_SPA_STAFF"), 403);
        call(get("/api/owner/spas/" + spa + "/services/" + service + "/health-questions"), as(owner, "ROLE_SPA_OWNER"), 200);
        // Once the guest withdraws it, the spa sees nothing.
        call(delete(mine), as(guest, "ROLE_USER"), 200);
        assertEquals("NONE", call(get(desk), as(owner, "ROLE_SPA_OWNER"), 200).get("status").asText());
    }

    @Test
    void answersAreStoredEncryptedAndBoundToTheBooking() throws Exception {
        long b = booking(guest, 48, "CONFIRMED");
        submit(b, guest, answers(true, "Warfarin 5mg"), 200);
        byte[] stored = db.queryForObject("SELECT ciphertext FROM booking_health_form WHERE booking_id=?", byte[].class, b);
        String raw = new String(stored, StandardCharsets.ISO_8859_1);
        assertFalse(raw.contains("Warfarin"));
        assertFalse(raw.contains("pregnant"));
        // Round trip, and a copy pasted onto another booking does not decrypt.
        assertTrue(new String(cipher.decrypt(b, stored), StandardCharsets.UTF_8).contains("Warfarin 5mg"));
        assertThrows(GeneralSecurityException.class, () -> cipher.decrypt(b + 1, stored));
        byte[] tampered = stored.clone();
        tampered[tampered.length - 1] ^= 1;
        assertThrows(GeneralSecurityException.class, () -> cipher.decrypt(b, tampered));
        // A different key cannot read it, and the spa is told the form is unreadable rather than shown garbage.
        var other = new HealthFormCipher(Base64.getEncoder().encodeToString(new byte[32]));
        assertThrows(GeneralSecurityException.class, () -> other.decrypt(b, stored));
        db.update("UPDATE booking_health_form SET ciphertext=? WHERE booking_id=?", tampered, b);
        assertEquals("UNREADABLE", forms.spaView(spa, b).get("status"));
    }

    @Test
    void withoutAKeyTheFeatureRefusesToStore() {
        var off = new HealthFormCipher("");
        assertFalse(off.enabled());
        var e = assertThrows(ResponseStatusException.class, () -> off.encrypt(1, new byte[]{1}));
        assertEquals(503, e.getStatusCode().value());
        assertTrue(e.getReason().contains("HEALTH_FORM_KEY"));
        assertFalse(new HealthFormCipher("too-short").enabled());
    }

    @Test
    void lastAnswersPrefillTheNextBooking() throws Exception {
        long first = booking(guest, 24, "CONFIRMED");
        var a = answers(false, "Thyroxine");
        submit(first, guest, a, 200);
        long next = booking(guest, 24 * 14, "CONFIRMED");
        var form = call(get("/api/me/bookings/" + next + "/health-form"), as(guest, "ROLE_USER"), 200);
        assertEquals("NONE", form.get("status").asText());
        assertTrue(form.get("prefilled").asBoolean());
        assertEquals("Thyroxine", form.at("/answers/medications").asText());
        // The spa sees nothing for the new booking until the guest confirms and submits.
        assertEquals("NONE", forms.spaView(spa, next).get("status"));
        // Another guest's answers never prefill this guest's form.
        long stranger = booking(otherGuest, 24, "CONFIRMED");
        assertFalse(call(get("/api/me/bookings/" + stranger + "/health-form"), as(otherGuest, "ROLE_USER"), 200).get("prefilled").asBoolean());
    }

    @Test
    void retentionDeletesFormsAfterTheVisit() throws Exception {
        long recent = booking(guest, 48, "CONFIRMED");
        long old = booking(guest, 48, "CONFIRMED");
        long cancelled = booking(guest, 48, "CONFIRMED");
        for (long b : List.of(recent, old, cancelled)) submit(b, guest, answers(false, ""), 200);
        // Visits that ended 10 and 91 days ago, and one that was cancelled.
        db.update("UPDATE booking SET start_ts=UTC_TIMESTAMP()-INTERVAL 10 DAY, end_ts=UTC_TIMESTAMP()-INTERVAL 10 DAY WHERE id=?", recent);
        db.update("UPDATE booking SET start_ts=UTC_TIMESTAMP()-INTERVAL 91 DAY, end_ts=UTC_TIMESTAMP()-INTERVAL 91 DAY WHERE id=?", old);
        db.update("UPDATE booking SET status='CANCELLED' WHERE id=?", cancelled);
        forms.purge(90);
        var left = db.queryForList("SELECT booking_id FROM booking_health_form WHERE booking_id IN (?,?,?)", Long.class, recent, old, cancelled);
        assertEquals(List.of(recent), left);
        // After the visit the guest can no longer change it.
        submit(recent, guest, answers(true, ""), 409);
    }
}
