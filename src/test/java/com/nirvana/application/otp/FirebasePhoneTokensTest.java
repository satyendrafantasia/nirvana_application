package com.nirvana.application.otp;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Google's proof that a phone code was confirmed: only a genuine, fresh, phone sign-in for this project counts. */
class FirebasePhoneTokensTest {
    private static final String PROJECT = "nirvana-test";
    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
    private static final KeyPair GOOGLE = keys();
    private static final KeyPair STRANGER = keys();
    private final AtomicInteger fetches = new AtomicInteger();

    private static KeyPair keys() {
        try {
            var g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private FirebasePhoneTokens tokens() {
        return new FirebasePhoneTokens(PROJECT, () -> {
            fetches.incrementAndGet();
            return new FirebasePhoneTokens.Fetched(Map.of("k1", GOOGLE.getPublic()), Duration.ofHours(6));
        }, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Map<String, Object> claims() {
        Map<String, Object> c = new HashMap<>();
        c.put("iss", "https://securetoken.google.com/" + PROJECT);
        c.put("aud", PROJECT);
        c.put("sub", "firebase-user-1");
        c.put("auth_time", NOW.minusSeconds(30).getEpochSecond());
        c.put("phone_number", "+919876543210");
        c.put("firebase", Map.of("sign_in_provider", "phone"));
        return c;
    }

    private static String sign(Map<String, Object> claims, KeyPair key, String kid) {
        return Jwts.builder().setHeaderParam("kid", kid).setClaims(claims)
                .setIssuedAt(Date.from(NOW.minusSeconds(30))).setExpiration(Date.from(NOW.plusSeconds(3600)))
                .signWith(key.getPrivate(), SignatureAlgorithm.RS256).compact();
    }

    @Test void aGenuinePhoneSignInProvesTheNumber() {
        String token = sign(claims(), GOOGLE, "k1");
        assertEquals("+919876543210", tokens().verifiedPhone(token));
        assertTrue(tokens().proves(token, "+91 98765 43210"));
        assertFalse(tokens().proves(token, "+919999999999"));
    }

    @Test void tokensNotSignedByGoogleAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> tokens().verifiedPhone(sign(claims(), STRANGER, "k1")));
        assertThrows(IllegalArgumentException.class, () -> tokens().verifiedPhone(sign(claims(), STRANGER, "unknown")));
        assertThrows(IllegalArgumentException.class, () -> tokens().verifiedPhone("not-a-token"));
    }

    @Test void anotherProjectsTokensAreRejected() {
        var c = claims();
        c.put("aud", "someone-elses-app");
        assertThrows(IllegalArgumentException.class, () -> tokens().verifiedPhone(sign(c, GOOGLE, "k1")));
        var d = claims();
        d.put("iss", "https://securetoken.google.com/someone-elses-app");
        assertThrows(IllegalArgumentException.class, () -> tokens().verifiedPhone(sign(d, GOOGLE, "k1")));
    }

    @Test void onlyARecentPhoneSignInCounts() {
        var email = claims();
        email.put("firebase", Map.of("sign_in_provider", "password"));
        assertThrows(IllegalArgumentException.class, () -> tokens().verifiedPhone(sign(email, GOOGLE, "k1")));
        var old = claims();
        old.put("auth_time", NOW.minus(Duration.ofMinutes(20)).getEpochSecond());
        assertThrows(IllegalArgumentException.class, () -> tokens().verifiedPhone(sign(old, GOOGLE, "k1")));
        var noPhone = claims();
        noPhone.remove("phone_number");
        assertThrows(IllegalArgumentException.class, () -> tokens().verifiedPhone(sign(noPhone, GOOGLE, "k1")));
    }

    @Test void googleAndPhoneProofsAreNotInterchangeable() {
        var c = claims();
        c.put("firebase", Map.of("sign_in_provider", "google.com"));
        c.put("email", "Customer@example.test");
        c.put("email_verified", true);
        String signed = sign(c, GOOGLE, "k1");
        assertEquals(new FirebasePhoneTokens.GoogleIdentity(PROJECT, "firebase-user-1", "customer@example.test"),
                tokens().verifiedGoogle(signed));
        assertThrows(IllegalArgumentException.class, () -> tokens().verifiedPhone(signed));
        assertThrows(IllegalArgumentException.class, () -> tokens().verifiedGoogle(sign(claims(), GOOGLE, "k1")));
        c.put("email_verified", false);
        assertThrows(IllegalArgumentException.class, () -> tokens().verifiedGoogle(sign(c, GOOGLE, "k1")));
    }

    @Test void googlesKeysAreFetchedOnceAndUnknownKeysDoNotForceRefetches() {
        var t = tokens();
        String good = sign(claims(), GOOGLE, "k1");
        t.verifiedPhone(good);
        t.verifiedPhone(good);
        for (int i = 0; i < 5; i++)
            assertThrows(IllegalArgumentException.class, () -> t.verifiedPhone(sign(claims(), STRANGER, "made-up")));
        assertEquals(1, fetches.get());
    }

    @Test void withoutAProjectNothingIsAccepted() {
        var t = new FirebasePhoneTokens("", () -> new FirebasePhoneTokens.Fetched(Map.of(), Duration.ZERO), Clock.systemUTC());
        assertFalse(t.configured());
        assertThrows(IllegalStateException.class, () -> t.verifiedPhone(sign(claims(), GOOGLE, "k1")));
    }
}
