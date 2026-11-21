package com.nirvana.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.model.Address;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.enums.KycStatus;
import com.nirvana.application.repository.SpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns spa leads into live, bookable spas once an admin confirms each spa agreed to be listed.
 * Every spa starts with the same starter menu, two therapists, a room per treatment, its Google
 * Maps hours, appointment times for the coming weeks, and a place in every active Nirvana
 * package. The spa can change all of it later.
 */
@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class LeadOnboardingService {
    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    static final int MAX_DAYS = 60;
    private static final Pattern STATE_AND_PIN = Pattern.compile(",\\s*([A-Za-z .]+?)\\s+(\\d{6})\\s*(?:,\\s*India)?\\s*$");

    /** A treatment every onboarded spa offers until it sets its own menu. */
    public record StarterService(String name, String description, int minutes, int priceRupees) {}

    public static final List<StarterService> STARTER_MENU = List.of(
            new StarterService("Swedish Massage", "Full-body massage with long, flowing strokes to relax muscles and ease stress.", 60, 1999),
            new StarterService("Deep Tissue Massage", "Firm-pressure massage that works into tight muscles and knots.", 60, 2499),
            new StarterService("Aromatherapy Massage", "Gentle full-body massage with essential oils.", 60, 2299));

    public record Result(int onboarded, int linked, int skipped, long slots, List<Map<String, Object>> spas, List<String> problems) {}

    private final JdbcTemplate jdbc;
    private final SpaRepository spas;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    /**
     * Onboards the given leads, or every lead not yet onboarded when {@code leadIds} is empty.
     * Each lead is its own transaction, so one bad row doesn't undo the rest.
     */
    public Result onboard(List<Long> leadIds, int days, Long adminId) {
        if (days < 1 || days > MAX_DAYS) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose between 1 and " + MAX_DAYS + " days of appointments.");
        List<Long> ids = leadIds == null || leadIds.isEmpty()
                ? jdbc.queryForList("SELECT id FROM spa_lead WHERE spa_id IS NULL AND status NOT IN ('ONBOARDED','NOT_INTERESTED') ORDER BY id", Long.class)
                : leadIds.stream().distinct().toList();
        int onboarded = 0, linked = 0, skipped = 0;
        long slots = 0;
        List<Map<String, Object>> created = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (Long id : ids) {
            try {
                Outcome outcome = tx.execute(status -> onboardOne(id, days, adminId));
                switch (Objects.requireNonNull(outcome).kind) {
                    case CREATED -> { onboarded++; slots += outcome.slots; created.add(Map.of("leadId", id, "spaId", outcome.spaId, "name", outcome.name)); }
                    case LINKED -> linked++;
                    case SKIPPED -> { skipped++; problems.add("Lead " + id + ": " + outcome.name); }
                }
            } catch (RuntimeException e) {
                skipped++;
                problems.add("Lead " + id + ": " + Optional.ofNullable(e.getMessage()).orElse(e.getClass().getSimpleName()));
            }
        }
        return new Result(onboarded, linked, skipped, slots, created, problems.size() > 50 ? problems.subList(0, 50) : problems);
    }

    private enum Kind { CREATED, LINKED, SKIPPED }

    private record Outcome(Kind kind, Long spaId, String name, long slots) {}

    private Outcome onboardOne(long leadId, int days, Long adminId) {
        var rows = jdbc.queryForList("SELECT * FROM spa_lead WHERE id=? FOR UPDATE", leadId);
        if (rows.isEmpty()) return new Outcome(Kind.SKIPPED, null, "not found", 0);
        Map<String, Object> lead = rows.get(0);
        if (lead.get("spa_id") != null) return new Outcome(Kind.SKIPPED, null, "already onboarded", 0);
        if ("NOT_INTERESTED".equals(lead.get("status"))) return new Outcome(Kind.SKIPPED, null, "marked not interested", 0);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String placeId = (String) lead.get("google_place_id");

        // Already on Nirvana under the same Google listing: link the lead instead of adding a twin.
        if (placeId != null) {
            List<Long> existing = jdbc.queryForList("SELECT id FROM spa WHERE google_place_id=? ORDER BY id LIMIT 1", Long.class, placeId);
            if (!existing.isEmpty()) {
                markOnboarded(leadId, existing.get(0), adminId, now, "Already on Nirvana as spa " + existing.get(0) + ".");
                return new Outcome(Kind.LINKED, existing.get(0), (String) lead.get("name"), 0);
            }
        }

        Map<DayOfWeek, LeadHours.Day> week = LeadHours.week(hours((String) lead.get("opening_hours_json")));
        if (week.isEmpty()) return new Outcome(Kind.SKIPPED, null, "listed as closed every day", 0);
        LeadHours.Day usual = usualHours(week);

        Spa spa = new Spa();
        String name = (String) lead.get("name");
        spa.setName(name);
        spa.setPhone((String) lead.get("phone"));
        spa.setEmail((String) lead.get("email"));
        // A link longer than the spa's field is almost always a pasted search URL; a cut-off link wouldn't work.
        spa.setWebsiteUrl(fits((String) lead.get("website"), 255));
        spa.setGooglePlaceId(placeId);
        spa.setGoogleMapsUrl(fits((String) lead.get("maps_url"), 500));
        spa.setTimezone(ZONE.getId());
        spa.setAddress(address(lead));
        spa.setIsActive(true);
        spa.setIsVerified(true);
        spa.setIsFeatured(false);
        spa.setKycStatus(KycStatus.APPROVED);
        spa.setKycRequestedAt(OffsetDateTime.now(ZoneOffset.UTC));
        spa.setKycApprovedAt(OffsetDateTime.now(ZoneOffset.UTC));
        // Guests book a time, not a person: the two starter therapists are placeholders.
        spa.setAllowTherapistSelection(false);
        spa.setAllowTherapistTypeSelection(false);
        spa.setMaxConcurrentServices(2);
        spa.setMaxAdvanceBookingDays(days);
        spa.setMinNoticeMinutes(60);
        spa.setOpenTimeLocal(usual.allDay() ? LocalTime.MIDNIGHT : usual.open());
        spa.setCloseTimeLocal(usual.allDay() ? LocalTime.of(23, 59) : usual.close());
        spa.setWorkingDays(EnumSet.copyOf(week.keySet()));
        spa.setMetaJson(write(Map.of("onboardedFromLead", leadId, "permission", "confirmed by admin", "starterMenu", true)));
        spa = spas.saveAndFlush(spa);
        long spaId = spa.getId();

        Timestamp stamp = utc(OffsetDateTime.now());
        for (var day : week.entrySet()) {
            LeadHours.Day d = day.getValue();
            jdbc.update("INSERT INTO schedule_rule (spa_id,weekday,open_local,close_local,is_holiday,version,note) VALUES (?,?,?,?,0,0,?)",
                    spaId, day.getKey().getValue(), d.allDay() ? LocalTime.MIDNIGHT : d.open(), d.allDay() ? LocalTime.MIDNIGHT : d.close(),
                    "From Google Maps at onboarding");
        }
        List<Long> therapists = new ArrayList<>();
        for (int t = 1; t <= 2; t++)
            therapists.add(insert("INSERT INTO therapist (created_at,updated_at,spa_id,name,display_name,type,currency,is_active,is_available,rating_avg,rating_count,version) VALUES (?,?,?,?,?,'massage','INR',1,1,0,0,0)",
                    stamp, stamp, spaId, "Therapist " + t, "Therapist " + t));

        long slotCount = 0;
        LocalDate today = LocalDate.now(ZONE);
        OffsetDateTime earliest = OffsetDateTime.now(ZONE).plusMinutes(60);
        for (int s = 0; s < STARTER_MENU.size(); s++) {
            StarterService item = STARTER_MENU.get(s);
            int cents = item.priceRupees() * 100;
            long serviceId = insert("""
                    INSERT INTO service (created_at,updated_at,spa_id,name,description,category,duration_min,duration_minutes,buffer_min,base_price_cents,price_cents,
                                         currency,gender_allowed,is_active,is_visible_on_marketplace,min_persons,max_persons,version)
                    VALUES (?,?,?,?,?,'massage',?,?,0,?,?,'INR','ANY',1,1,1,1,0)""",
                    stamp, stamp, spaId, item.name(), item.description(), item.minutes(), item.minutes(), cents, cents);
            for (long therapist : therapists) jdbc.update("INSERT INTO therapist_service (therapist_id,service_id) VALUES (?,?)", therapist, serviceId);
            // One room per treatment, so each treatment can be offered at every hour; the two
            // therapists cap how many guests are seen at once.
            long roomId = insert("INSERT INTO spa_room (created_at,updated_at,spa_id,name,capacity,is_active,version) VALUES (?,?,?,?,1,1,0)",
                    stamp, stamp, spaId, "Treatment room " + (s + 1));
            List<Object[]> slots = new ArrayList<>(), claimed = new ArrayList<>();
            for (int d = 0; d < days; d++) {
                LocalDate date = today.plusDays(d);
                LeadHours.Day hours = week.get(date.getDayOfWeek());
                if (hours == null) continue;
                for (LocalTime start : hours.slotStarts(item.minutes())) {
                    OffsetDateTime at = date.atTime(start).atZone(ZONE).toOffsetDateTime();
                    if (at.isBefore(earliest)) continue;
                    slots.add(new Object[]{stamp, stamp, spaId, serviceId, roomId, utc(at), utc(at.plusMinutes(item.minutes()))});
                    // Same key the owner's slot generator writes, so it won't add a second slot here.
                    claimed.add(new Object[]{roomId, Timestamp.from(at.toInstant())});
                }
            }
            jdbc.batchUpdate("INSERT INTO generated_room_slot VALUES (?,?)", claimed);
            jdbc.batchUpdate("""
                    INSERT INTO slot (created_at,updated_at,spa_id,service_id,room_id,start_ts,end_ts,capacity_unit,booked_units,is_blocked,status,version)
                    VALUES (?,?,?,?,?,?,?,1,0,0,'OPEN',0)""", slots);
            slotCount += slots.size();
        }
        // Guests holding a Nirvana package can use it here, like at every other live spa.
        jdbc.update("INSERT IGNORE INTO service_package_spa (package_id, spa_id) SELECT id, ? FROM packages WHERE is_active=1", spaId);
        markOnboarded(leadId, spaId, adminId, now, null);
        return new Outcome(Kind.CREATED, spaId, name, slotCount);
    }

    /**
     * Keeps every spa onboarded from a lead bookable: adds appointment times so each treatment
     * room has them through the spa's booking window (its max advance days), using the spa's
     * current weekly hours. Only days after a room's last appointment are added, and a time the
     * owner's slot tool already made is skipped, so running it again adds nothing.
     */
    public long topUpSlots() {
        long added = 0;
        for (Long spaId : jdbc.queryForList("""
                SELECT DISTINCT s.id FROM spa s JOIN spa_lead l ON l.spa_id=s.id
                WHERE l.status='ONBOARDED' AND s.is_active=1 ORDER BY s.id""", Long.class)) {
            try {
                added += Objects.requireNonNull(tx.execute(status -> topUpSpa(spaId)));
            } catch (RuntimeException e) {
                log.warn("Could not add appointment times for spa {}: {}", spaId, e.getMessage());
            }
        }
        return added;
    }

    private long topUpSpa(long spaId) {
        Integer window = jdbc.queryForObject("SELECT max_advance_booking_days FROM spa WHERE id=?", Integer.class, spaId);
        int days = Math.min(window == null || window < 1 ? 30 : window, MAX_DAYS);
        Map<DayOfWeek, LeadHours.Day> week = new EnumMap<>(DayOfWeek.class);
        for (var rule : jdbc.queryForList("SELECT weekday, open_local, close_local FROM schedule_rule WHERE spa_id=? AND is_holiday=0", spaId)) {
            LocalTime open = ((java.sql.Time) rule.get("open_local")).toLocalTime(), close = ((java.sql.Time) rule.get("close_local")).toLocalTime();
            week.put(DayOfWeek.of(((Number) rule.get("weekday")).intValue()),
                    new LeadHours.Day(open, close, open.equals(LocalTime.MIDNIGHT) && close.equals(LocalTime.MIDNIGHT)));
        }
        if (week.isEmpty()) return 0;
        LocalDate today = LocalDate.now(ZONE);
        OffsetDateTime earliest = OffsetDateTime.now(ZONE).plusMinutes(60);
        Timestamp stamp = utc(OffsetDateTime.now());
        long added = 0;
        // Each treatment keeps the room it was given at onboarding.
        for (var room : jdbc.queryForList("""
                SELECT sl.service_id, sl.room_id, sv.duration_min, MAX(sl.start_ts) last_start
                FROM slot sl JOIN service sv ON sv.id=sl.service_id AND sv.is_active=1
                             JOIN spa_room r ON r.id=sl.room_id AND r.is_active=1
                WHERE sl.spa_id=? GROUP BY sl.service_id, sl.room_id, sv.duration_min""", spaId)) {
            long serviceId = ((Number) room.get("service_id")).longValue(), roomId = ((Number) room.get("room_id")).longValue();
            int minutes = ((Number) room.get("duration_min")).intValue();
            LocalDate lastDay = ((Timestamp) room.get("last_start")).toLocalDateTime().atOffset(ZoneOffset.UTC).atZoneSameInstant(ZONE).toLocalDate();
            List<Object[]> slots = new ArrayList<>();
            for (LocalDate date = lastDay.isBefore(today) ? today : lastDay.plusDays(1); date.isBefore(today.plusDays(days)); date = date.plusDays(1)) {
                LeadHours.Day hours = week.get(date.getDayOfWeek());
                if (hours == null) continue;
                for (LocalTime start : hours.slotStarts(minutes)) {
                    OffsetDateTime at = date.atTime(start).atZone(ZONE).toOffsetDateTime();
                    if (at.isBefore(earliest)) continue;
                    // Claim the time first; one the owner's slot tool already made is left alone.
                    if (jdbc.update("INSERT IGNORE INTO generated_room_slot VALUES (?,?)", roomId, Timestamp.from(at.toInstant())) == 0) continue;
                    slots.add(new Object[]{stamp, stamp, spaId, serviceId, roomId, utc(at), utc(at.plusMinutes(minutes))});
                }
            }
            jdbc.batchUpdate("""
                    INSERT INTO slot (created_at,updated_at,spa_id,service_id,room_id,start_ts,end_ts,capacity_unit,booked_units,is_blocked,status,version)
                    VALUES (?,?,?,?,?,?,?,1,0,0,'OPEN',0)""", slots);
            added += slots.size();
        }
        return added;
    }

    private void markOnboarded(long leadId, long spaId, Long adminId, LocalDateTime now, String note) {
        jdbc.update("""
                UPDATE spa_lead SET spa_id=?, status='ONBOARDED', permission_confirmed_by=?, permission_confirmed_at=?, updated_at=?,
                       notes=CASE WHEN ? IS NULL THEN notes WHEN notes IS NULL THEN ? ELSE CONCAT(notes, '\n', ?) END
                WHERE id=?""", spaId, adminId, now, now, note, note, note, leadId);
    }

    /** The hours most days share, shown on the spa page. */
    private static LeadHours.Day usualHours(Map<DayOfWeek, LeadHours.Day> week) {
        Map<LeadHours.Day, Integer> counts = new HashMap<>();
        week.values().forEach(d -> counts.merge(d, 1, Integer::sum));
        return Collections.max(counts.entrySet(), Map.Entry.comparingByValue()).getKey();
    }

    private Map<String, String> hours(String stored) {
        if (stored == null) return null;
        try {
            return json.readValue(stored, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private static Address address(Map<String, Object> lead) {
        String line = Optional.ofNullable((String) lead.get("address")).orElse((String) lead.get("name"));
        Address a = new Address();
        a.setAddressLine(line.length() > 255 ? line.substring(0, 255) : line);
        a.setFormattedAddress(line.length() > 500 ? line.substring(0, 500) : line);
        a.setCity(Optional.ofNullable((String) lead.get("city")).orElse("Gurugram"));
        Matcher m = STATE_AND_PIN.matcher(line);
        if (m.find()) {
            a.setState(m.group(1).strip());
            a.setPostalCode(m.group(2));
        }
        a.setCountry("India");
        a.setCountryCode("IN");
        a.setLatitude((BigDecimal) lead.get("latitude"));
        a.setLongitude((BigDecimal) lead.get("longitude"));
        a.setTimezone(ZONE.getId());
        a.setAddressType("SPA_BRANCH");
        a.setGeoSource("GOOGLE_MAPS_LEAD");
        return a;
    }

    private static String fits(String value, int max) {
        return value != null && value.length() <= max ? value : null;
    }

    /** Times are stored as UTC wall-clock values, as the app's JPA mapping reads them. */
    private static Timestamp utc(OffsetDateTime at) {
        return Timestamp.valueOf(at.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());
    }

    private long insert(String sql, Object... args) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
