package com.nirvana.application.service.recovery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Signed, expiring links for checkout reminders. A token names the recovery row, a random nonce
 * stored on that row, what the link is for and when it stops working, all signed with HMAC-SHA256.
 * It carries no personal data: no email, phone, name or booking reference.
 */
@Component
public class CheckoutRecoveryTokens {
    public enum Purpose {
        /** Back to the booking flow; also records the click. */
        RESUME("r"),
        /** Turns checkout reminders off. */
        UNSUBSCRIBE("u");
        final String code;
        Purpose(String code) { this.code = code; }
        static Optional<Purpose> of(String code) {
            for (Purpose p : values()) if (p.code.equals(code)) return Optional.of(p);
            return Optional.empty();
        }
    }

    public record Claims(long recoveryId, String nonce, Purpose purpose, Instant expiresAt) {}

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String VERSION = "cr1";

    private final byte[] key;

    public CheckoutRecoveryTokens(@Value("${checkout-recovery.token-secret:}") String secret,
                                  @Value("${security.jwt.secret:}") String jwtSecret) {
        // A dedicated secret if one is set; otherwise a key derived from the JWT secret, so a
        // reminder link can never be used as a sign-in token or the other way round.
        String base = secret != null && !secret.isBlank() ? secret : jwtSecret;
        this.key = base == null || base.isBlank() ? new byte[0] : hmac(base.getBytes(StandardCharsets.UTF_8),
                "nirvana-checkout-recovery-v1".getBytes(StandardCharsets.US_ASCII));
    }

    public boolean configured() {
        return key.length > 0;
    }

    public static String newNonce() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public String sign(long recoveryId, String nonce, Purpose purpose, Instant expiresAt) {
        if (!configured()) throw new IllegalStateException("Checkout recovery links are not configured");
        String payload = VERSION + ":" + purpose.code + ":" + recoveryId + ":" + nonce + ":" + expiresAt.getEpochSecond();
        byte[] body = payload.getBytes(StandardCharsets.US_ASCII);
        return B64.encodeToString(body) + "." + B64.encodeToString(hmac(key, body));
    }

    /** The claims of a genuine, unexpired token for this purpose; empty for anything else. */
    public Optional<Claims> verify(String token, Purpose expected, Instant now) {
        if (!configured() || token == null || token.length() > 256) return Optional.empty();
        int dot = token.indexOf('.');
        if (dot <= 0 || dot != token.lastIndexOf('.')) return Optional.empty();
        try {
            byte[] body = B64D.decode(token.substring(0, dot));
            byte[] signature = B64D.decode(token.substring(dot + 1));
            if (!MessageDigest.isEqual(hmac(key, body), signature)) return Optional.empty();
            String[] parts = new String(body, StandardCharsets.US_ASCII).split(":");
            if (parts.length != 5 || !VERSION.equals(parts[0])) return Optional.empty();
            Optional<Purpose> purpose = Purpose.of(parts[1]);
            if (purpose.isEmpty() || purpose.get() != expected) return Optional.empty();
            Instant expires = Instant.ofEpochSecond(Long.parseLong(parts[4]));
            if (!expires.isAfter(now)) return Optional.empty();
            if (!parts[3].matches("[0-9a-f]{32}")) return Optional.empty();
            return Optional.of(new Claims(Long.parseLong(parts[2]), parts[3], purpose.get(), expires));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }
}
