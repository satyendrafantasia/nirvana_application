package com.nirvana.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spas to approach about joining Nirvana. Imported from Google Maps style exports
 * (title, address, phone, email, website, socials, totalScore, reviewsCount, url, coordinates).
 */
@Service
@RequiredArgsConstructor
public class SpaLeadService {
    public static final List<String> STATUSES = List.of("NEW", "CONTACTED", "INTERESTED", "ONBOARDED", "NOT_INTERESTED");
    static final int MAX_IMPORT = 5000;
    private static final Pattern CID = Pattern.compile("[?&]cid=(\\d{1,25})");
    private static final Pattern STATE_AND_PIN = Pattern.compile("^[A-Za-z .]+ \\d{6}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]+\\.[A-Za-z]{2,}$");
    private static final Set<String> PLACEHOLDER_EMAIL_DOMAINS = Set.of("example.com", "mail.com", "example.org", "test.com");

    private final JdbcTemplate jdbc;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public record ImportResult(int created, int updated, int skipped, List<String> problems) {}

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /**
     * Adds new spas and refreshes the contact details of ones already imported (matched by Google
     * Maps cid). Status and notes your team has recorded are never overwritten.
     */
    @Transactional
    public ImportResult importLeads(JsonNode input, String source, String cityOverride) {
        // Two shapes: a plain list of spas, or a Google Maps search response with "local_results".
        JsonNode rows = input != null && input.isObject() && input.path("local_results").isArray() ? input.get("local_results") : input;
        if (rows == null || !rows.isArray()) throw bad("Upload a JSON list of spas, or a Google Maps search result with local_results.");
        if (rows.size() > MAX_IMPORT) throw bad("Import at most " + MAX_IMPORT + " spas at a time.");
        String src = clip(blankToNull(source), 100);
        if (src == null) src = "import";
        int created = 0, updated = 0, skipped = 0;
        List<String> problems = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            JsonNode row = rows.get(i);
            String name = clip(text(row, "title"), 255);
            if (name == null) { skipped++; problems.add("Row " + (i + 1) + ": no name"); continue; }
            String cid = Optional.ofNullable(cid(text(row, "url"))).orElse(digits(text(row, "data_cid")));
            String placeId = placeId(text(row, "place_id"));
            String key = cid != null ? "cid:" + cid : placeId != null ? "place:" + placeId : null;
            if (key != null && !seen.add(key)) { skipped++; problems.add("Row " + (i + 1) + " (" + name + "): duplicate in this file"); continue; }
            String address = clip(text(row, "address"), 500);
            String city = clip(Optional.ofNullable(blankToNull(cityOverride)).orElse(city(address)), 100);
            String phone = clip(text(row, "phone"), 32);
            String email = email(text(row, "email"));
            String website = clip(httpUrl(text(row, "website")), 1000);
            String socials = socials(row.path("socials"));
            BigDecimal rating = decimal(first(row, "totalScore", "rating"), 1, 0, 5);
            JsonNode reviewNode = first(row, "reviewsCount", "reviews");
            Integer reviews = reviewNode.canConvertToInt() && reviewNode.asInt() >= 0 ? reviewNode.asInt() : null;
            JsonNode at = row.path("coordinates").isObject() ? row.path("coordinates") : row.path("gps_coordinates");
            BigDecimal lat = decimal(first(at, "lat", "latitude"), 6, -90, 90);
            BigDecimal lng = decimal(first(at, "lng", "longitude"), 6, -180, 180);
            String mapsUrl = clip(Optional.ofNullable(httpUrl(text(row, "url")))
                    .orElse(cid != null ? "https://maps.google.com/?cid=" + cid : null), 500);

            // The same spa may arrive by cid in one export and by Place ID in another.
            Long existing = cid == null ? null : jdbc.query("SELECT id FROM spa_lead WHERE google_cid=?",
                    rs -> rs.next() ? rs.getLong(1) : null, cid);
            if (existing == null && placeId != null)
                existing = jdbc.query("SELECT id FROM spa_lead WHERE google_place_id=?", rs -> rs.next() ? rs.getLong(1) : null, placeId);
            if (existing == null) {
                jdbc.update("""
                        INSERT INTO spa_lead (google_cid,google_place_id,name,address,city,phone,email,website,socials_json,google_rating,
                                              google_review_count,latitude,longitude,maps_url,source,status,imported_at,updated_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'NEW',?,?)""",
                        cid, placeId, name, address, city, phone, email, website, socials, rating, reviews, lat, lng, mapsUrl, src, now(), now());
                created++;
            } else {
                // Keep what an earlier export knew if this one leaves it out (Place ID, cid, email, socials).
                jdbc.update("""
                        UPDATE spa_lead SET google_cid=COALESCE(google_cid,?),google_place_id=COALESCE(?,google_place_id),name=?,address=?,
                                            city=?,phone=COALESCE(?,phone),email=COALESCE(?,email),website=COALESCE(?,website),
                                            socials_json=COALESCE(?,socials_json),google_rating=?,google_review_count=?,latitude=?,
                                            longitude=?,maps_url=COALESCE(?,maps_url),updated_at=? WHERE id=?""",
                        cid, placeId, name, address, city, phone, email, website, socials, rating, reviews, lat, lng, mapsUrl, now(), existing);
                updated++;
            }
        }
        return new ImportResult(created, updated, skipped, problems.size() > 50 ? problems.subList(0, 50) : problems);
    }

    public Map<String, Object> list(String status, String query, int page, int size) {
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) throw bad("Unknown status.");
        if (page < 0 || size < 1 || size > 100) throw bad("Invalid page.");
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) { where.append(" AND status=?"); args.add(status); }
        if (query != null && !query.isBlank()) {
            where.append(" AND (name LIKE ? OR address LIKE ? OR phone LIKE ?)");
            String like = "%" + query.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            args.addAll(List.of(like, like, like));
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM spa_lead" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);
        var rows = jdbc.queryForList("""
                SELECT id, google_cid AS googleCid, google_place_id AS googlePlaceId, name, address, city, phone, email, website, socials_json AS socialsJson,
                       google_rating AS googleRating, google_review_count AS googleReviewCount, latitude, longitude,
                       maps_url AS mapsUrl, source, status, notes, spa_id AS spaId, imported_at AS importedAt,
                       last_contacted_at AS lastContactedAt
                FROM spa_lead""" + where + " ORDER BY FIELD(status,'INTERESTED','NEW','CONTACTED','ONBOARDED','NOT_INTERESTED'), google_review_count DESC, id LIMIT ? OFFSET ?",
                pageArgs.toArray());
        var counts = new LinkedHashMap<String, Long>();
        STATUSES.forEach(s -> counts.put(s, 0L));
        jdbc.query("SELECT status, COUNT(*) FROM spa_lead GROUP BY status", rs -> { counts.put(rs.getString(1), rs.getLong(2)); });
        return Map.of("content", rows, "total", total == null ? 0 : total, "page", page, "counts", counts);
    }

    /** Records progress with a lead. Marking it contacted stamps the time. */
    @Transactional
    public void update(long id, String status, String notes) {
        if (status != null && !STATUSES.contains(status)) throw bad("Unknown status.");
        int changed = jdbc.update("""
                UPDATE spa_lead SET status=COALESCE(?,status), notes=?, updated_at=?,
                       last_contacted_at=CASE WHEN ? IN ('CONTACTED','INTERESTED') AND (status<>? OR last_contacted_at IS NULL) THEN ? ELSE last_contacted_at END
                WHERE id=?""",
                status, clip(blankToNull(notes), 2000), now(), status, status, now(), id);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found.");
    }

    // ---------- parsing ----------

    static String cid(String url) {
        if (url == null) return null;
        Matcher m = CID.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    /** "…, Sector 45, Gurugram, Haryana 122003, India" → "Gurugram". */
    static String digits(String value) {
        return value != null && value.matches("\\d{1,25}") ? value : null;
    }

    static String placeId(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{10,255}") ? value : null;
    }

    private static JsonNode first(JsonNode node, String... fields) {
        for (String f : fields) if (!node.path(f).isMissingNode() && !node.path(f).isNull()) return node.path(f);
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    static String city(String address) {
        if (address == null) return null;
        String[] parts = address.split("\\s*,\\s*");
        for (int i = parts.length - 1; i > 0; i--)
            if (STATE_AND_PIN.matcher(parts[i]).matches()) return parts[i - 1];
        return null;
    }

    static String email(String value) {
        String v = blankToNull(value);
        if (v == null || !EMAIL.matcher(v).matches()) return null;
        String domain = v.substring(v.indexOf('@') + 1).toLowerCase(Locale.ROOT);
        String local = v.substring(0, v.indexOf('@')).toLowerCase(Locale.ROOT);
        return PLACEHOLDER_EMAIL_DOMAINS.contains(domain) && local.equals("example") ? null : clip(v, 255);
    }

    static String httpUrl(String value) {
        String v = blankToNull(value);
        return v != null && v.matches("(?i)https?://\\S+") ? v : null;
    }

    private String socials(JsonNode node) {
        if (!node.isObject()) return null;
        Map<String, String> clean = new TreeMap<>();
        node.fields().forEachRemaining(e -> {
            String url = httpUrl(e.getValue().asText(null));
            if (url != null && e.getKey().matches("[a-z]{1,20}")) clean.put(e.getKey(), clip(url, 500));
        });
        try {
            return clean.isEmpty() ? null : json.writeValueAsString(clean);
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal decimal(JsonNode node, int scale, double min, double max) {
        if (!node.isNumber() || node.asDouble() < min || node.asDouble() > max) return null;
        return node.decimalValue().setScale(scale, RoundingMode.HALF_UP);
    }

    private static String text(JsonNode row, String field) {
        JsonNode v = row.path(field);
        return v.isTextual() ? blankToNull(v.asText()) : null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static String clip(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
