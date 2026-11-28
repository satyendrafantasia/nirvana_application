package com.nirvana.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nirvana.application.model.dto.AddressDTO;
import com.nirvana.application.model.dto.SpaOnboardingStartRequest;
import com.nirvana.application.model.enums.KycStatus;
import com.nirvana.application.repository.SpaRepository;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

/**
 * An owner claims their spa's Google Maps listing. Google details only pre-fill the form; what is
 * saved is what the owner confirmed and submitted, linked to the listing by its Place ID.
 */
@Service
@RequiredArgsConstructor
public class ListingClaimService {
    public static final String TERMS_VERSION = "partner-2026-11";
    public static final String DECLARATION =
            "I own or am authorised to manage this spa. The details I submitted are correct, and I agree to the Nirvana partner terms.";
    static final int MAX_PENDING_CLAIMS = 5;

    private final CityPlacesClient places;
    private final ListingClaimPhoneService phoneChecks;
    private final SpaOnboardingService onboarding;
    private final SpaRepository spas;
    private final JdbcTemplate jdbc;
    private final ClaimableInventory inventory;

    public record ClaimRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,255}") String placeId,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Pattern(regexp = "[+0-9 ()-]{6,30}", message = "Enter a valid phone number") String phone,
            @Email @Size(max = 200) String email,
            @Size(max = 500) @Pattern(regexp = "(https?://\\S+)?", message = "Website must start with http:// or https://") String websiteUrl,
            @Size(max = 2000) String description,
            @NotBlank @Size(max = 255) String addressLine,
            @Size(max = 255) String locality,
            @NotBlank @Size(max = 100) String city,
            @Size(max = 100) String state,
            @Size(max = 20) String postalCode,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @NotBlank @Size(max = 64) String timezone,
            @NotBlank @Size(max = 200) String ownerName,
            @AssertTrue(message = "Confirm that you are authorised to list this spa") boolean authorised,
            @NotBlank String termsVersion,
            /** From verifying the listing's phone by SMS; absent when the owner skipped it. */
            @Size(max = 64) String phoneVerificationId) {}

    /** Spa listings on Google matching the owner's search, each marked if it is already on Nirvana. */
    public List<Map<String, Object>> search(String query, String country) {
        List<JsonNode> found = new ArrayList<>();
        places.claimSearch(query, country).path("places").forEach(place -> {
            if (place.path("id").asText().matches("[A-Za-z0-9_-]{1,255}") && SpaPlaceFilter.isDiscoverySpa(place)) found.add(place);
        });
        var listed = inventory.lookup(found.stream().map(p -> p.path("id").asText()).toList());
        List<Map<String, Object>> results = new ArrayList<>();
        for (JsonNode place : found) {
            String id = place.path("id").asText();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("placeId", id);
            row.put("name", place.path("displayName").path("text").asText(""));
            row.put("address", place.path("formattedAddress").asText(""));
            row.put("businessStatus", place.path("businessStatus").asText(""));
            var listing = listed.get(id);
            row.put("alreadyListed", listing != null);
            row.put("claimableExisting", listing != null && listing.claimable());
            results.add(row);
        }
        return results;
    }

    /** Google's details shaped as the claim form. Nothing here is stored. */
    public Map<String, Object> prefill(String placeId) {
        inventory.available(placeId);
        JsonNode place = places.claimDetails(placeId);
        if (!SpaPlaceFilter.isSpa(place)) throw notSpa();
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("placeId", placeId);
        form.put("name", place.path("displayName").path("text").asText(""));
        form.put("phone", place.path("internationalPhoneNumber").asText(""));
        form.put("listingHasPhone", ListingClaimPhoneService.e164(place.path("internationalPhoneNumber").asText("")) != null);
        form.put("websiteUrl", place.path("websiteUri").asText(""));
        form.put("description", place.path("editorialSummary").path("text").asText(""));
        form.put("addressLine", place.path("formattedAddress").asText(""));
        form.put("locality", component(place, "sublocality_level_1", "sublocality", "neighborhood"));
        form.put("city", component(place, "locality", "administrative_area_level_3", "administrative_area_level_2"));
        form.put("state", component(place, "administrative_area_level_1"));
        form.put("postalCode", component(place, "postal_code"));
        String country = shortComponent(place, "country");
        form.put("countryCode", country);
        form.put("timezone", "IN".equalsIgnoreCase(country) || country.isEmpty() ? "Asia/Kolkata" : "");
        if (place.path("location").has("latitude")) {
            form.put("latitude", place.path("location").path("latitude").decimalValue().setScale(6, java.math.RoundingMode.HALF_UP));
            form.put("longitude", place.path("location").path("longitude").decimalValue().setScale(6, java.math.RoundingMode.HALF_UP));
        }
        form.put("termsVersion", TERMS_VERSION);
        form.put("declaration", DECLARATION);
        return form;
    }

    /** Creates the owner's spa, unpublished and pending review, and records their consent. */
    @Transactional
    public Map<String, Object> submit(Long userId, ClaimRequest request, String clientIp, String userAgent) {
        if (!TERMS_VERSION.equals(request.termsVersion()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The partner terms have changed. Reload the page and review them again.");
        try {
            ZoneId.of(request.timezone());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid time zone.");
        }
        // The remote call must finish before locking a live spa and its booking schedule.
        if (!SpaPlaceFilter.isSpa(places.claimDetails(request.placeId()))) throw notSpa();
        jdbc.queryForList("SELECT id FROM spa WHERE google_place_id=? FOR UPDATE", Long.class, request.placeId());
        var existing = inventory.available(request.placeId());
        Long pending = jdbc.queryForObject("SELECT COUNT(*) FROM spa_listing_claim WHERE user_id=? AND status='PENDING'", Long.class, userId);
        if (pending != null && pending >= MAX_PENDING_CLAIMS)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "You have " + pending + " claims waiting for review. Please wait for those to be reviewed.");
        String phoneCheck = trim(request.phoneVerificationId());
        java.time.LocalDateTime phoneVerifiedAt = phoneCheck == null ? null : phoneChecks.consume(userId, request.placeId(), phoneCheck);

        if (existing != null) {
            // Record the claimant’s confirmed details for review without editing the live profile.
            var profile = new org.json.JSONObject();
            profile.put("ownerName", trim(request.ownerName()));
            profile.put("name", trim(request.name()));
            profile.put("phone", trim(request.phone()));
            profile.put("email", trim(request.email()));
            profile.put("websiteUrl", trim(request.websiteUrl()));
            profile.put("addressLine", trim(request.addressLine()));
            profile.put("locality", trim(request.locality()));
            profile.put("city", trim(request.city()));
            profile.put("state", trim(request.state()));
            profile.put("postalCode", trim(request.postalCode()));
            profile.put("countryCode", request.countryCode());
            profile.put("latitude", coordinate(request.latitude()));
            profile.put("longitude", coordinate(request.longitude()));
            profile.put("timezone", request.timezone());
            profile.put("description", trim(request.description()));
            return recordClaim(existing.spaId(), userId, request, phoneVerifiedAt, phoneCheck, clientIp, userAgent, profile.toString());
        }
        AddressDTO address = AddressDTO.builder()
                .addressLine(trim(request.addressLine())).locality(trim(request.locality())).city(trim(request.city()))
                .state(trim(request.state())).postalCode(trim(request.postalCode()))
                .countryCode(request.countryCode().toUpperCase(Locale.ROOT)).formattedAddress(trim(request.addressLine()))
                .latitude(coordinate(request.latitude())).longitude(coordinate(request.longitude())).timezone(request.timezone())
                .googlePlaceId(request.placeId()).build();
        var summary = onboarding.startOnboarding(userId, SpaOnboardingStartRequest.builder()
                .name(trim(request.name())).phone(trim(request.phone())).email(trim(request.email()))
                .websiteUrl(trim(request.websiteUrl())).description(trim(request.description())).address(address).build());
        var spa = spas.findById(summary.getId()).orElseThrow();
        spa.setOwnerName(trim(request.ownerName()));
        spa.setGooglePlaceId(request.placeId());
        if (spa.getAddress() != null) spa.getAddress().setGeoSource("OWNER_CONFIRMED");
        spa.setKycStatus(KycStatus.PENDING);
        spa.setKycRequestedAt(OffsetDateTime.now(ZoneOffset.UTC));
        spas.saveAndFlush(spa);

        return recordClaim(spa.getId(), userId, request, phoneVerifiedAt, phoneCheck, clientIp, userAgent, null);
    }

    private Map<String, Object> recordClaim(long spaId, Long userId, ClaimRequest request,
            java.time.LocalDateTime phoneVerifiedAt, String phoneCheck, String clientIp, String userAgent, String profile) {
        jdbc.update("""
                INSERT INTO spa_listing_claim (spa_id,user_id,google_place_id,terms_version,declaration,consented_at,
                    phone_verified_at,phone_verification_id,client_ip,user_agent,status,existing_inventory,submitted_profile)
                VALUES (?,?,?,?,?,?,?,?,?,?,'PENDING',?,?)""",
                spaId, userId, request.placeId(), TERMS_VERSION, DECLARATION, OffsetDateTime.now(ZoneOffset.UTC),
                phoneVerifiedAt, phoneCheck, cut(clientIp, 64), cut(userAgent, 255), profile != null, profile);
        return Map.of("spaId", spaId, "name", request.name(), "status", "PENDING",
                "phoneVerified", phoneVerifiedAt != null, "existingInventory", profile != null);
    }

    /** Claims for the review queue, newest first. */
    public List<Map<String, Object>> list(String status) {
        return jdbc.queryForList("""
                SELECT c.id, c.spa_id AS spaId, c.existing_inventory AS existingInventory,
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(c.submitted_profile,'$.name')),s.name) AS spaName,
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(c.submitted_profile,'$.city')),s.city) AS city, c.google_place_id AS placeId,
                       c.status, c.consented_at AS consentedAt, c.terms_version AS termsVersion,
                       u.email AS ownerEmail, COALESCE(JSON_UNQUOTE(JSON_EXTRACT(c.submitted_profile,'$.ownerName')),s.owner_name) AS ownerName, c.review_note AS reviewNote,
                       c.reviewed_at AS reviewedAt, c.phone_verified_at AS phoneVerifiedAt, CASE WHEN c.existing_inventory THEN JSON_UNQUOTE(JSON_EXTRACT(c.submitted_profile,'$.phone')) ELSE s.phone END AS phone,
                       CASE WHEN c.existing_inventory THEN JSON_UNQUOTE(JSON_EXTRACT(c.submitted_profile,'$.email')) ELSE s.email END AS spaEmail,
                       CASE WHEN c.existing_inventory THEN JSON_UNQUOTE(JSON_EXTRACT(c.submitted_profile,'$.websiteUrl')) ELSE s.website_url END AS websiteUrl,
                       CASE WHEN c.existing_inventory THEN JSON_UNQUOTE(JSON_EXTRACT(c.submitted_profile,'$.addressLine')) ELSE s.address_line1 END AS addressLine,
                       CASE WHEN c.existing_inventory THEN JSON_UNQUOTE(JSON_EXTRACT(c.submitted_profile,'$.state')) ELSE s.state END AS state,
                       CASE WHEN c.existing_inventory THEN JSON_UNQUOTE(JSON_EXTRACT(c.submitted_profile,'$.postalCode')) ELSE s.postal_code END AS postalCode, s.kyc_status AS kycStatus
                FROM spa_listing_claim c JOIN spa s ON s.id=c.spa_id JOIN app_user u ON u.id=c.user_id
                WHERE c.status=? ORDER BY c.consented_at DESC LIMIT 200""", status);
    }

    @Transactional
    public void approve(long claimId, Long adminId, String verificationNote) {
        var claim = pendingClaim(claimId);
        long spaId = ((Number) claim.get("spa_id")).longValue();
        if (isExisting(claim)) {
            String note = trim(verificationNote);
            if (claim.get("phone_verified_at") == null && note == null)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Verify ownership using the listing’s phone and record the callback before approving.");
            jdbc.update("INSERT INTO spa_manager(user_id) VALUES (?) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)", claim.get("user_id"));
            int assigned = jdbc.update("""
                    UPDATE spa s JOIN spa_listing_claim c ON c.spa_id=s.id
                    JOIN spa_manager m ON m.user_id=c.user_id JOIN app_user u ON u.id=m.user_id
                    JOIN role r ON r.id=u.role_id AND r.role_type='Spa_MANAGER'
                    SET s.spa_manager_id=m.id, s.owner_name=JSON_UNQUOTE(JSON_EXTRACT(c.submitted_profile,'$.ownerName')), s.version=s.version+1
                    WHERE c.id=? AND s.google_place_id=c.google_place_id
                      AND u.active=1 AND u.is_active=1 AND u.deleted_at IS NULL
                    """ + " AND " + ClaimableInventory.UNASSIGNED_IMPORT, claimId);
            if (assigned != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Ownership or listing eligibility changed. Review this claim again.");
            review(claimId, "APPROVED", adminId, note);
            return;
        }
        var spa = spas.findById(spaId).orElseThrow();
        if (!java.util.Objects.equals(spa.getGooglePlaceId(), claim.get("google_place_id")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This spa is no longer linked to the claimed listing. Reject the claim instead.");
        spa.setIsActive(true);
        spa.setIsVerified(true);
        spa.setKycStatus(KycStatus.APPROVED);
        spa.setKycApprovedAt(OffsetDateTime.now(ZoneOffset.UTC));
        spas.saveAndFlush(spa);
        review(claimId, "APPROVED", adminId, null);
    }

    /** Rejecting frees the listing so its real owner can claim it. */
    @Transactional
    public void reject(long claimId, Long adminId, String note) {
        var claim = pendingClaim(claimId);
        if (isExisting(claim)) {
            review(claimId, "REJECTED", adminId, note);
            return;
        }
        var spa = spas.findById(((Number) claim.get("spa_id")).longValue()).orElseThrow();
        spa.setIsActive(false);
        spa.setIsVerified(false);
        spa.setGooglePlaceId(null);
        spa.setKycStatus(KycStatus.REJECTED);
        spa.setKycRejectedAt(OffsetDateTime.now(ZoneOffset.UTC));
        spa.setKycRejectedReason(note);
        spas.saveAndFlush(spa);
        review(claimId, "REJECTED", adminId, note);
    }

    private Map<String, Object> pendingClaim(long claimId) {
        var rows = jdbc.queryForList("SELECT * FROM spa_listing_claim WHERE id=? FOR UPDATE", claimId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found.");
        if (!"PENDING".equals(rows.get(0).get("status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This claim has already been reviewed.");
        return rows.get(0);
    }

    private void review(long claimId, String status, Long adminId, String note) {
        jdbc.update("UPDATE spa_listing_claim SET status=?, reviewed_at=?, reviewed_by=?, review_note=? WHERE id=?",
                status, OffsetDateTime.now(ZoneOffset.UTC), adminId, note, claimId);
    }

    private static String component(JsonNode place, String... types) {
        return findComponent(place, types).path("longText").asText("");
    }

    private static String shortComponent(JsonNode place, String... types) {
        return findComponent(place, types).path("shortText").asText("");
    }

    private static JsonNode findComponent(JsonNode place, String... types) {
        for (String type : types)
            for (JsonNode c : place.path("addressComponents"))
                for (JsonNode t : c.path("types"))
                    if (type.equals(t.asText())) return c;
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private static boolean isExisting(Map<String, Object> claim) {
        Object value = claim.get("existing_inventory");
        return Boolean.TRUE.equals(value) || (value instanceof Number n && n.intValue() == 1);
    }

    static ResponseStatusException alreadyListed() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "This spa is already on Nirvana or has a claim under review. If it is yours, contact partner support.");
    }

    private static ResponseStatusException notSpa() {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Google Maps does not list this business as a spa. Create your spa from scratch instead.");
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static BigDecimal coordinate(BigDecimal value) {
        return value == null ? null : value.setScale(6, java.math.RoundingMode.HALF_UP);
    }

    private static String cut(String value, int max) {
        return value == null ? null : value.length() > max ? value.substring(0, max) : value;
    }
}
