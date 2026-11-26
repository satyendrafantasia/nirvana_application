package com.nirvana.application.service.health;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;

/**
 * Guest health and consent forms. The answers are encrypted before storage (see {@link HealthFormCipher}),
 * readable only by the guest who booked and by the front desk of the booking's spa, and never written to
 * logs, exports or emails: nothing outside this class reads the table.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class HealthFormService {
    public static final int MAX_QUESTIONS = 5;
    public static final String DEFAULT_TERMS = "I confirm that the answers above are true and complete, and that I will tell "
        + "the spa before my treatment if anything changes. I understand the spa relies on them to treat me safely, "
        + "that I may stop a treatment at any time, and that the spa may decline or adapt a treatment for my safety. "
        + "I agree to the spa keeping these answers for this appointment.";
    private static final Set<String> OPEN = Set.of("PENDING_PAYMENT", "CONFIRMED", "ARRIVED");
    private static final String ISO = "'%Y-%m-%dT%H:%i:%sZ'";

    private final JdbcTemplate db;
    private final HealthFormCipher cipher;
    private final ObjectMapper json;

    public enum Allergy { OILS, NUTS, LATEX }
    public enum Pressure { LIGHT, MEDIUM, FIRM }
    public record CustomAnswer(Long questionId, String prompt, String answer) {}
    /** The guest's answers. Yes/no questions are Boolean so an unanswered one can be told apart from "no". */
    public record Answers(Boolean pregnant, Set<Allergy> allergies, String allergyDetails,
                          Boolean recentInjuryOrSurgery, String injuryDetails, Boolean skinCondition, String skinDetails,
                          Boolean heartCondition, String medications, Pressure pressure, String avoidAreas, String notes,
                          List<CustomAnswer> custom) {}
    /** {@code confirmedCurrent}: the guest confirmed that answers carried over from an earlier visit are still true. */
    public record Submission(Answers answers, boolean consent, boolean confirmedCurrent) {}
    /** What is stored, encrypted: the answers plus the exact terms the guest agreed to. */
    public record Stored(Answers answers, String terms, boolean confirmedCurrent) {}
    public record Question(Long id, String prompt) {}

    private record BookingRef(long id, long user, long spa, long service, String status, boolean upcoming) {}

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private BookingRef booking(long id) {
        var rows = db.query("SELECT id,user_id,spa_id,service_id,status,end_ts>UTC_TIMESTAMP() FROM booking WHERE id=?",
            (r, i) -> new BookingRef(r.getLong(1), r.getLong(2), r.getLong(3), r.getLong(4), r.getString(5), r.getBoolean(6)), id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        return rows.get(0);
    }

    private BookingRef guestBooking(long id, long user) {
        var b = booking(id);
        if (b.user() != user) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This is not your booking");
        return b;
    }

    // ---- questions and terms the spa sets ----

    @Transactional(readOnly = true)
    public List<Question> questions(long spa, long service) {
        return db.query("SELECT id,prompt FROM service_health_question WHERE spa_id=? AND service_id=? ORDER BY position,id",
            (r, i) -> new Question(r.getLong(1), r.getString(2)), spa, service);
    }

    public List<Question> replaceQuestions(long spa, long service, List<String> prompts) {
        Integer owned = db.queryForObject("SELECT COUNT(*) FROM service WHERE id=? AND spa_id=?", Integer.class, service, spa);
        if (owned == null || owned == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found in this spa");
        var clean = (prompts == null ? List.<String>of() : prompts).stream()
            .map(p -> p == null ? "" : p.trim()).filter(p -> !p.isEmpty()).toList();
        if (clean.size() > MAX_QUESTIONS) throw bad("Add at most " + MAX_QUESTIONS + " questions per service");
        if (clean.stream().anyMatch(p -> p.length() > 300)) throw bad("Keep each question under 300 characters");
        // Keep the ids of questions that stay, so answers already given still line up with them.
        var existing = questions(spa, service);
        var kept = new HashSet<Long>();
        for (int i = 0; i < clean.size(); i++) {
            String prompt = clean.get(i);
            var same = existing.stream().filter(q -> q.prompt().equals(prompt) && !kept.contains(q.id())).findFirst();
            if (same.isPresent()) {
                kept.add(same.get().id());
                db.update("UPDATE service_health_question SET position=? WHERE id=?", i, same.get().id());
            } else {
                db.update("INSERT INTO service_health_question(spa_id,service_id,position,prompt,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6))",
                    spa, service, i, prompt);
            }
        }
        for (var q : existing) if (!kept.contains(q.id())) db.update("DELETE FROM service_health_question WHERE id=?", q.id());
        return questions(spa, service);
    }

    @Transactional(readOnly = true)
    public String terms(long spa) {
        var rows = db.queryForList("SELECT terms FROM spa_health_terms WHERE spa_id=?", String.class, spa);
        return rows.isEmpty() ? DEFAULT_TERMS : rows.get(0);
    }

    public String setTerms(long spa, String terms) {
        String clean = terms == null ? "" : terms.trim();
        if (clean.isEmpty()) {
            db.update("DELETE FROM spa_health_terms WHERE spa_id=?", spa);
            return DEFAULT_TERMS;
        }
        if (clean.length() > 2000) throw bad("Keep the consent terms under 2000 characters");
        db.update("INSERT INTO spa_health_terms(spa_id,terms,updated_at) VALUES(?,?,UTC_TIMESTAMP(6)) "
            + "ON DUPLICATE KEY UPDATE terms=VALUES(terms),updated_at=VALUES(updated_at)", spa, clean);
        return clean;
    }

    // ---- the guest ----

    @Transactional(readOnly = true)
    public Map<String, Object> guestView(long bookingId, long user) {
        var b = guestBooking(bookingId, user);
        var questions = questions(b.spa(), b.service());
        var view = new LinkedHashMap<String, Object>();
        view.put("bookingId", b.id());
        view.put("enabled", cipher.enabled());
        view.put("editable", cipher.enabled() && b.upcoming() && OPEN.contains(b.status()));
        view.put("terms", terms(b.spa()));
        view.put("questions", questions);
        view.put("status", "NONE");
        view.put("prefilled", false);
        if (!cipher.enabled()) {
            view.put("message", HealthFormCipher.UNAVAILABLE);
            return view;
        }
        var own = load(b.id(), "booking_id=? AND spa_id=? AND user_id=?", b.id(), b.spa(), user);
        if (own != null) {
            view.put("status", own.stored() == null ? "UNREADABLE" : "SUBMITTED");
            view.put("updatedAt", own.updatedAt());
            if (own.stored() != null) view.put("answers", withQuestions(own.stored().answers(), questions));
            return view;
        }
        // Prefill from the guest's most recent earlier form that is still kept. Only the standard answers
        // carry over; the spa sees nothing until the guest confirms them and submits.
        var ids = db.queryForList("SELECT booking_id FROM booking_health_form WHERE user_id=? AND booking_id<>? ORDER BY updated_at DESC LIMIT 1",
            Long.class, user, b.id());
        if (!ids.isEmpty()) {
            var last = load(ids.get(0), "booking_id=? AND user_id=?", ids.get(0), user);
            if (last != null && last.stored() != null) {
                var a = last.stored().answers();
                view.put("answers", withQuestions(new Answers(a.pregnant(), a.allergies(), a.allergyDetails(), a.recentInjuryOrSurgery(),
                    a.injuryDetails(), a.skinCondition(), a.skinDetails(), a.heartCondition(), a.medications(), a.pressure(),
                    a.avoidAreas(), a.notes(), List.of()), questions));
                view.put("prefilled", true);
                view.put("prefilledFrom", last.updatedAt());
            }
        }
        return view;
    }

    /** The answers laid out against the service's current questions, keeping saved answers by question id. */
    private static Answers withQuestions(Answers a, List<Question> questions) {
        Map<Long, String> saved = new HashMap<>();
        if (a.custom() != null) for (var c : a.custom()) if (c.questionId() != null) saved.put(c.questionId(), c.answer());
        var custom = questions.stream().map(q -> new CustomAnswer(q.id(), q.prompt(), saved.getOrDefault(q.id(), ""))).toList();
        return new Answers(a.pregnant(), a.allergies(), a.allergyDetails(), a.recentInjuryOrSurgery(), a.injuryDetails(),
            a.skinCondition(), a.skinDetails(), a.heartCondition(), a.medications(), a.pressure(), a.avoidAreas(), a.notes(), custom);
    }

    public Map<String, Object> submit(long bookingId, long user, Submission input) {
        var b = guestBooking(bookingId, user);
        if (!cipher.enabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, HealthFormCipher.UNAVAILABLE);
        if (!OPEN.contains(b.status()) || !b.upcoming())
            throw new IllegalStateException("The health form can only be changed before the appointment ends");
        if (input == null || input.answers() == null) throw bad("Answer the health questions");
        if (!input.consent()) throw bad("Agree to the spa's terms to submit the form");
        var a = input.answers();
        if (a.pregnant() == null || a.recentInjuryOrSurgery() == null || a.skinCondition() == null || a.heartCondition() == null)
            throw bad("Answer every yes or no question");
        Map<Long, String> given = new HashMap<>();
        if (a.custom() != null)
            for (var c : a.custom()) if (c != null && c.questionId() != null) given.put(c.questionId(), text(c.answer(), 500));
        // Only this service's questions are kept, with the wording the guest saw.
        var custom = questions(b.spa(), b.service()).stream()
            .map(q -> new CustomAnswer(q.id(), q.prompt(), given.getOrDefault(q.id(), ""))).toList();
        var allergies = EnumSet.noneOf(Allergy.class);
        if (a.allergies() != null) a.allergies().stream().filter(Objects::nonNull).forEach(allergies::add);
        var clean = new Answers(a.pregnant(), allergies, text(a.allergyDetails(), 500), a.recentInjuryOrSurgery(),
            text(a.injuryDetails(), 500), a.skinCondition(), text(a.skinDetails(), 500), a.heartCondition(),
            text(a.medications(), 500), a.pressure() == null ? Pressure.MEDIUM : a.pressure(), text(a.avoidAreas(), 500),
            text(a.notes(), 1000), custom);
        byte[] plain;
        try {
            plain = json.writeValueAsBytes(new Stored(clean, terms(b.spa()), input.confirmedCurrent()));
        } catch (IOException e) {
            throw new IllegalStateException("Could not save the health form");
        }
        byte[] sealed = cipher.encrypt(b.id(), plain);
        Arrays.fill(plain, (byte) 0);
        db.update("""
            INSERT INTO booking_health_form(booking_id,spa_id,user_id,key_id,ciphertext,consented_at,updated_at)
            VALUES(?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE spa_id=VALUES(spa_id),user_id=VALUES(user_id),key_id=VALUES(key_id),
              ciphertext=VALUES(ciphertext),consented_at=VALUES(consented_at),updated_at=VALUES(updated_at)
            """, b.id(), b.spa(), b.user(), cipher.keyId(), sealed);
        return guestView(bookingId, user);
    }

    private static String text(String value, int max) {
        if (value == null) return "";
        String v = value.trim();
        // A generic message: the rejected text itself is never echoed back or logged.
        if (v.length() > max) throw bad("Keep each answer under " + max + " characters");
        return v;
    }

    /** The guest withdraws the form: it is deleted, and the spa no longer sees it. */
    public void withdraw(long bookingId, long user) {
        guestBooking(bookingId, user);
        db.update("DELETE FROM booking_health_form WHERE booking_id=? AND user_id=?", bookingId, user);
    }

    // ---- the spa ----

    /** The form for the spa's front desk. The booking must belong to this spa. */
    @Transactional(readOnly = true)
    public Map<String, Object> spaView(long spa, long bookingId) {
        var b = booking(bookingId);
        if (b.spa() != spa) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found in this spa");
        var view = new LinkedHashMap<String, Object>();
        view.put("enabled", cipher.enabled());
        view.put("status", "NONE");
        view.put("attention", List.of());
        if (!cipher.enabled()) return view;
        var form = load(bookingId, "booking_id=? AND spa_id=? AND user_id=?", bookingId, spa, b.user());
        if (form == null) return view;
        view.put("updatedAt", form.updatedAt());
        view.put("consentedAt", form.consentedAt());
        if (form.stored() == null) {
            view.put("status", "UNREADABLE");
            return view;
        }
        view.put("status", "SUBMITTED");
        view.put("answers", form.stored().answers());
        view.put("terms", form.stored().terms());
        view.put("attention", attention(form.stored().answers()));
        return view;
    }

    /** What the therapist must know before treating: shown as a flag on the appointment. */
    public static List<String> attention(Answers a) {
        var out = new ArrayList<String>();
        if (Boolean.TRUE.equals(a.pregnant())) out.add("Pregnant");
        if (Boolean.TRUE.equals(a.heartCondition())) out.add("High blood pressure or heart condition");
        if (a.allergies() != null && !a.allergies().isEmpty())
            out.add("Allergic to " + String.join(", ", a.allergies().stream().sorted().map(x -> x.name().toLowerCase()).toList()));
        if (Boolean.TRUE.equals(a.recentInjuryOrSurgery())) out.add("Recent injury or surgery");
        if (Boolean.TRUE.equals(a.skinCondition())) out.add("Skin condition");
        if (a.medications() != null && !a.medications().isBlank()) out.add("Takes medication");
        return out;
    }

    private record Loaded(Stored stored, String updatedAt, String consentedAt) {}

    /** Reads and decrypts one form; {@code stored} is null when it cannot be decrypted (for example, the key changed). */
    private Loaded load(long bookingId, String where, Object... args) {
        var rows = db.query("SELECT ciphertext,DATE_FORMAT(updated_at," + ISO + "),DATE_FORMAT(consented_at," + ISO
                + ") FROM booking_health_form WHERE " + where,
            (r, i) -> new Object[]{r.getBytes(1), r.getString(2), r.getString(3)}, args);
        if (rows.isEmpty()) return null;
        var row = rows.get(0);
        try {
            byte[] plain = cipher.decrypt(bookingId, (byte[]) row[0]);
            var stored = json.readValue(plain, Stored.class);
            Arrays.fill(plain, (byte) 0);
            return new Loaded(stored, (String) row[1], (String) row[2]);
        } catch (GeneralSecurityException | IOException e) {
            return new Loaded(null, (String) row[1], (String) row[2]);
        }
    }

    // ---- retention ----

    /**
     * Deletes forms kept past their purpose: {@code days} after the appointment ended, at once for a
     * cancelled booking, and any form whose booking has since moved to another spa or guest.
     */
    public int purge(int days) {
        return db.update("""
            DELETE f FROM booking_health_form f JOIN booking b ON b.id=f.booking_id
            WHERE b.end_ts < UTC_TIMESTAMP() - INTERVAL ? DAY OR b.status='CANCELLED'
               OR b.spa_id<>f.spa_id OR b.user_id<>f.user_id
            """, Math.max(1, days));
    }
}
