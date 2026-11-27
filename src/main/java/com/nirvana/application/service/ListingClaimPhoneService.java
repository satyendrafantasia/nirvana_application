package com.nirvana.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nirvana.application.config.SmsProperties;
import com.nirvana.application.otp.OtpGenerator;
import com.nirvana.application.otp.sender.SmsSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Proves a claimant can answer the phone on the Google listing: a code goes by SMS to the number
 * Google shows for that place, read live — never to a number the claimant typed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListingClaimPhoneService {
    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final Duration VERIFIED_FOR = Duration.ofMinutes(30);
    static final int MAX_ATTEMPTS = 5;
    static final int MAX_PER_PHONE = 3;
    static final Duration PHONE_WINDOW = Duration.ofMinutes(15);
    static final int MAX_PER_USER = 5;
    static final Duration USER_WINDOW = Duration.ofHours(1);
    static final Duration RESEND_AFTER = Duration.ofSeconds(60);

    private final CityPlacesClient places;
    private final JdbcTemplate jdbc;
    private final ClaimableInventory inventory;
    private final OtpGenerator generator;
    private final SmsSender sms;
    private final SmsProperties smsProperties;
    private final com.nirvana.application.otp.FirebasePhoneTokens phoneTokens;

    /** Local development only: write codes to the log when SMS is switched off. */
    @Value("${listing-claims.log-codes:false}")
    private boolean logCodes;

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    @Transactional
    public Map<String, Object> send(Long userId, String placeId) {
        if (!smsProperties.phoneCodesAvailable() && !logCodes)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Text messages are not available right now. Submit without verifying and our team will call the spa.");
        // Text only spas that can still be claimed, so this cannot be used to message any business on Google.
        inventory.available(placeId);
        JsonNode place = places.claimDetails(placeId);
        if (!SpaPlaceFilter.isSpa(place))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Google Maps does not list this business as a spa. Create your spa from scratch instead.");
        String display = place.path("internationalPhoneNumber").asText("");
        String phone = e164(display);
        if (phone == null)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Your Google listing has no mobile number we can text. Submit without verifying and our team will call the spa.");
        LocalDateTime now = now();
        Long recentForUser = jdbc.queryForObject("SELECT COUNT(*) FROM listing_claim_phone_check WHERE user_id=? AND created_at>?",
                Long.class, userId, now.minus(USER_WINDOW));
        Long recentForPhone = jdbc.queryForObject("SELECT COUNT(*) FROM listing_claim_phone_check WHERE phone_number=? AND created_at>?",
                Long.class, phone, now.minus(PHONE_WINDOW));
        if (recentForUser >= MAX_PER_USER || recentForPhone >= MAX_PER_PHONE)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many codes requested. Please wait a few minutes and try again.");
        Long justSent = jdbc.queryForObject("SELECT COUNT(*) FROM listing_claim_phone_check WHERE user_id=? AND google_place_id=? AND created_at>?",
                Long.class, userId, placeId, now.minus(RESEND_AFTER));
        if (justSent > 0)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "A code was just sent. Wait a minute before asking for another.");

        String verificationId = UUID.randomUUID().toString();
        String code = generator.generateNumericOtp(6);
        jdbc.update("INSERT INTO listing_claim_phone_check (verification_id,user_id,google_place_id,phone_number,code_hash,created_at,expires_at) VALUES (?,?,?,?,?,?,?)",
                verificationId, userId, placeId, phone, hash(verificationId, code), now, now.plus(CODE_TTL));
        String name = place.path("displayName").path("text").asText("your spa");
        if (name.length() > 40) name = name.substring(0, 40);
        String message = "Nirvana: " + code + " is your code to claim " + name + " on Nirvana. It expires in "
                + CODE_TTL.toMinutes() + " minutes. If you did not ask for this, ignore this message.";
        if (smsProperties.isFirebase()) {
            // Firebase texts the listing's number from the browser; the number is public on Google Maps.
            return Map.of("verificationId", verificationId, "phone", mask(display), "phoneNumber", phone,
                    "expiresInMinutes", CODE_TTL.toMinutes(), "resendAfterSeconds", RESEND_AFTER.toSeconds());
        }
        if (smsProperties.isEnabled()) {
            try {
                sms.sendOtpSms(phone, message);
            } catch (RuntimeException e) {
                log.warn("Listing claim SMS failed for verification {}", verificationId, e);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "We could not send the text message. Please try again.");
            }
        } else {
            log.info("[local] Listing claim code for {}: {}", verificationId, code);
        }
        return Map.of("verificationId", verificationId, "phone", mask(display),
                "expiresInMinutes", CODE_TTL.toMinutes(), "resendAfterSeconds", RESEND_AFTER.toSeconds());
    }

    /** A wrong code is not rolled back, so it still counts as an attempt. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Map<String, Object> verify(Long userId, String verificationId, String code, String firebaseIdToken) {
        var rows = jdbc.queryForList("SELECT * FROM listing_claim_phone_check WHERE verification_id=? AND user_id=? FOR UPDATE", verificationId, userId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ask for a new code.");
        var row = rows.get(0);
        if (row.get("verified_at") != null) return Map.of("verified", true);
        int attempts = ((Number) row.get("attempts")).intValue();
        if (attempts >= MAX_ATTEMPTS)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many wrong codes. Ask for a new code.");
        if (time(row.get("expires_at")).isBefore(now()))
            throw new ResponseStatusException(HttpStatus.GONE, "This code has expired. Ask for a new code.");
        boolean match = firebaseIdToken != null && !firebaseIdToken.isBlank()
                ? provesListingPhone(firebaseIdToken, String.valueOf(row.get("phone_number")))
                : code != null && code.matches("\\d{6}") && MessageDigest.isEqual(
                hash(verificationId, code).getBytes(StandardCharsets.US_ASCII),
                String.valueOf(row.get("code_hash")).getBytes(StandardCharsets.US_ASCII));
        if (!match) {
            jdbc.update("UPDATE listing_claim_phone_check SET attempts=attempts+1 WHERE id=?", row.get("id"));
            int left = MAX_ATTEMPTS - attempts - 1;
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    left > 0 ? "That code is not right. " + left + (left == 1 ? " try" : " tries") + " left." : "Too many wrong codes. Ask for a new code.");
        }
        jdbc.update("UPDATE listing_claim_phone_check SET verified_at=? WHERE id=?", now(), row.get("id"));
        return Map.of("verified", true);
    }

    /** Uses up a verified check for this claim, returning when the phone was verified. Call inside the claim's transaction. */
    public LocalDateTime consume(Long userId, String placeId, String verificationId) {
        var rows = jdbc.queryForList("""
                SELECT id, verified_at FROM listing_claim_phone_check
                WHERE verification_id=? AND user_id=? AND google_place_id=? AND verified_at IS NOT NULL AND consumed_at IS NULL FOR UPDATE""",
                verificationId, userId, placeId);
        if (rows.isEmpty() || time(rows.get(0).get("verified_at")).isBefore(now().minus(VERIFIED_FOR)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Your phone verification has expired. Verify again, or submit without it.");
        jdbc.update("UPDATE listing_claim_phone_check SET consumed_at=? WHERE id=?", now(), rows.get(0).get("id"));
        return time(rows.get(0).get("verified_at"));
    }

    private boolean provesListingPhone(String firebaseIdToken, String phone) {
        try {
            return smsProperties.isFirebase() && phoneTokens.proves(firebaseIdToken, phone);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return false;
        }
    }

    private static LocalDateTime time(Object value) {
        return value instanceof java.sql.Timestamp t ? t.toLocalDateTime() : (LocalDateTime) value;
    }

    /** "+91 80 5550 1234" → "+918055501234"; null when it is not an international number we can text. */
    static String e164(String display) {
        if (display == null || !display.strip().startsWith("+")) return null;
        String digits = display.replaceAll("\\D", "");
        return digits.length() >= 8 && digits.length() <= 15 ? "+" + digits : null;
    }

    /** Keeps the country code and last four digits: "+91 80 5550 1234" → "+91 •• •••• 1234". */
    static String mask(String display) {
        String s = display.strip();
        int firstSpace = s.indexOf(' ');
        int keepFrom = s.length();
        for (int seen = 0; keepFrom > 0 && seen < 4; ) if (Character.isDigit(s.charAt(--keepFrom))) seen++;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            out.append(Character.isDigit(c) && i > firstSpace && i < keepFrom ? '•' : c);
        }
        return out.toString();
    }

    private static String hash(String verificationId, String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((verificationId + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
