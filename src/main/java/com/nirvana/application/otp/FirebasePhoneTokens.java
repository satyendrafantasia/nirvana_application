package com.nirvana.application.otp;

import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SigningKeyResolverAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the ID token Firebase gives the browser after a phone code is confirmed, following
 * Firebase's rules for verifying ID tokens without the Admin SDK: an RS256 signature by one of
 * Google's current keys, this project as audience and issuer, a phone sign-in, and a sign-in made
 * within the last few minutes. Google's keys are fetched once and kept for as long as it allows.
 */
@Slf4j
@Component
public class FirebasePhoneTokens {
    static final String KEYS_URL = "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";
    /** The proof must come from a sign-in this recent; an hour-old token is not proof of the phone now. */
    static final Duration FRESH_FOR = Duration.ofMinutes(10);

    private final String projectId;
    private final Supplier<Fetched> fetchKeys;
    private final Clock clock;
    private volatile Map<String, PublicKey> keys = Map.of();
    private volatile Instant keysExpireAt = Instant.EPOCH;
    private volatile Instant lastFetchAt = Instant.EPOCH;

    record Fetched(Map<String, PublicKey> keys, Duration keepFor) {}

    @Autowired
    public FirebasePhoneTokens(@Value("${firebase.project-id:}") String projectId, RestTemplateBuilder builder) {
        this(projectId, googleKeys(builder), Clock.systemUTC());
    }

    FirebasePhoneTokens(String projectId, Supplier<Fetched> fetchKeys, Clock clock) {
        this.projectId = projectId;
        this.fetchKeys = fetchKeys;
        this.clock = clock;
    }

    public boolean configured() {
        return StringUtils.hasText(projectId);
    }

    /** The phone number (as +digits) that Google confirmed with this token; throws if the token is not a valid, fresh phone proof. */
    public String verifiedPhone(String idToken) {
        if (!configured()) throw new IllegalStateException("Phone verification by Google is not set up.");
        if (!StringUtils.hasText(idToken)) throw invalid();
        Claims claims;
        try {
            claims = Jwts.parserBuilder()
                    .setClock(() -> Date.from(clock.instant()))
                    .setAllowedClockSkewSeconds(60)
                    .requireIssuer("https://securetoken.google.com/" + projectId)
                    .requireAudience(projectId)
                    .setSigningKeyResolver(new SigningKeyResolverAdapter() {
                        @Override
                        public Key resolveSigningKey(JwsHeader header, Claims c) {
                            if (!"RS256".equals(header.getAlgorithm())) throw new JwtException("Unexpected algorithm");
                            PublicKey key = key(header.getKeyId());
                            if (key == null) throw new JwtException("Unknown signing key");
                            return key;
                        }
                    })
                    .build()
                    .parseClaimsJws(idToken)
                    .getBody();
        } catch (JwtException | IllegalArgumentException e) {
            log.info("Rejected a Firebase phone token: {}", e.getMessage());
            throw invalid();
        }
        Instant now = clock.instant();
        Object provider = claims.get("firebase") instanceof Map<?, ?> f ? f.get("sign_in_provider") : null;
        Number authTime = claims.get("auth_time", Number.class);
        String phone = claims.get("phone_number", String.class);
        if (!StringUtils.hasText(claims.getSubject()) || !"phone".equals(provider) || !StringUtils.hasText(phone)
                || authTime == null || Instant.ofEpochSecond(authTime.longValue()).isBefore(now.minus(FRESH_FOR))
                || Instant.ofEpochSecond(authTime.longValue()).isAfter(now.plusSeconds(60)))
            throw invalid();
        return e164(phone);
    }

    /** Whether the token proves this phone number; formatting differences ("+91 98765 43210") don't matter. */
    public boolean proves(String idToken, String phone) {
        String expected = e164(phone);
        return expected != null && expected.equals(verifiedPhone(idToken));
    }

    /** "+91 98765 43210" → "+919876543210"; null without a country code. */
    public static String e164(String phone) {
        if (phone == null || !phone.strip().startsWith("+")) return null;
        String digits = phone.replaceAll("\\D", "");
        return digits.length() >= 8 && digits.length() <= 15 ? "+" + digits : null;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("That phone verification didn't work. Ask for a new code and try again.");
    }

    private PublicKey key(String kid) {
        if (kid == null) return null;
        PublicKey key = clock.instant().isBefore(keysExpireAt) ? keys.get(kid) : null;
        if (key != null) return key;
        synchronized (this) {
            // Refresh when the keys expire, or (at most once a minute) when a token names a key we
            // haven't seen, as Google may have just started using it.
            Instant now = clock.instant();
            boolean expired = !now.isBefore(keysExpireAt);
            boolean unknown = !keys.containsKey(kid) && !now.isBefore(lastFetchAt.plusSeconds(60));
            if (expired || unknown) {
                Fetched fetched = fetchKeys.get();
                keys = fetched.keys();
                keysExpireAt = now.plus(fetched.keepFor());
                lastFetchAt = now;
            }
            return keys.get(kid);
        }
    }

    private static final Pattern MAX_AGE = Pattern.compile("max-age=(\\d+)");

    private static Supplier<Fetched> googleKeys(RestTemplateBuilder builder) {
        var http = builder.connectTimeout(Duration.ofSeconds(5)).readTimeout(Duration.ofSeconds(10)).build();
        return () -> {
            try {
                var response = http.getForEntity(KEYS_URL, JsonNode.class);
                Map<String, PublicKey> keys = new HashMap<>();
                var certs = CertificateFactory.getInstance("X.509");
                var body = response.getBody();
                if (body != null) body.fields().forEachRemaining(e -> {
                    try {
                        keys.put(e.getKey(), certs.generateCertificate(
                                new ByteArrayInputStream(e.getValue().asText().getBytes(StandardCharsets.US_ASCII))).getPublicKey());
                    } catch (Exception bad) {
                        log.warn("Skipped an unreadable Google signing certificate {}", e.getKey());
                    }
                });
                String cacheControl = response.getHeaders().getCacheControl();
                Matcher m = MAX_AGE.matcher(cacheControl == null ? "" : cacheControl);
                return new Fetched(Map.copyOf(keys), m.find() ? Duration.ofSeconds(Long.parseLong(m.group(1))) : Duration.ofHours(1));
            } catch (RestClientException | java.security.cert.CertificateException e) {
                log.warn("Could not fetch Google's Firebase signing keys: {}", e.getMessage());
                return new Fetched(Map.of(), Duration.ofMinutes(1));
            }
        };
    }
}
