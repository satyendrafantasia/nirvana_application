package com.nirvana.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class OwnerPush {
    // Eligibility is checked again at delivery and when answering a booking after ownership changes.
    private static final String CLAIM = """
            FROM spa_listing_claim c JOIN spa s ON s.id=c.spa_id AND s.google_place_id=c.google_place_id
            JOIN spa_manager m ON m.id=s.spa_manager_id AND m.user_id=c.user_id
            WHERE c.status='APPROVED'
            """;
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final RestClient http;
    private GoogleCredentials credentials;
    @Value("${firebase.project-id:}") String project;
    @Value("${firebase.web-api-key:}") String apiKey;
    @Value("${FIREBASE_WEB_APP_ID:}") String appId;
    @Value("${FIREBASE_MESSAGING_SENDER_ID:}") String sender;
    @Value("${FIREBASE_WEB_PUSH_PUBLIC_KEY:}") String vapid;
    @Value("${FIREBASE_PUSH_ENABLED:false}") boolean enabled;

    public OwnerPush(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4000);
        factory.setReadTimeout(8000);
        http = RestClient.builder().requestFactory(factory).build();
    }

    public boolean ready() {
        return enabled && !project.isBlank() && !apiKey.isBlank() && !appId.isBlank()
                && !sender.isBlank() && !vapid.isBlank();
    }

    public synchronized boolean credentialsAvailable() {
        if (!ready()) return false;
        try { loadCredentials(); return true; }
        catch (IOException | RuntimeException unavailable) { return false; }
    }

    public boolean claimed(long spa, long user) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 " + CLAIM
                + " AND s.id=? AND m.user_id=?)", Boolean.class, spa, user));
    }

    public Map<String,Object> config(long spa, long user) {
        if (!claimed(spa,user)) return Map.of("eligible",false);
        return Map.of("eligible",true,"enabled",credentialsAvailable(),"vapidKey",vapid,"firebase",
                Map.of("apiKey",apiKey,"appId",appId,"projectId",project,"messagingSenderId",sender));
    }

    public void subscribe(long spa, long user, String token) {
        if (!claimed(spa,user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (!credentialsAvailable()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Firebase sender credentials are unavailable");
        db.update("INSERT INTO owner_push_device(user_id,token_hash,registration_token) VALUES (?,SHA2(?,256),?) "
                + "ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)",user,token,token);
    }

    public boolean subscribed(long spa,long user,String token) {
        if (!claimed(spa,user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM owner_push_device WHERE user_id=? AND token_hash=SHA2(?,256))",Boolean.class,user,token));
    }

    public void unsubscribe(long user,String token) {
        db.update("DELETE FROM owner_push_device WHERE user_id=? AND token_hash=SHA2(?,256)",user,token);
    }

    public void enqueue(long booking,long spa,int revision,String event) {
        if (!ready()) return;
        db.update("INSERT INTO booking_alert_job(booking_id,event_kind,audience,revision,device_id) "
                + "SELECT DISTINCT ?, ?, 'SPA_PUSH', ?, d.id FROM owner_push_device d "
                + "WHERE d.user_id IN (SELECT m.user_id " + CLAIM + " AND s.id=?) "
                + "ON DUPLICATE KEY UPDATE id=booking_alert_job.id",booking,event,revision,spa);
    }

    private void loadCredentials() throws IOException {
        if (credentials == null) credentials = GoogleCredentials.getApplicationDefault()
                .createScoped(List.of("https://www.googleapis.com/auth/firebase.messaging"));
    }

    private synchronized String accessToken() throws IOException {
        loadCredentials();
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }

    public void send(long device,long booking,long spa,String event) {
        if (!ready()) throw new IllegalStateException("Push is unavailable");
        var tokens=db.queryForList("SELECT registration_token FROM owner_push_device WHERE id=? "
                + "AND user_id IN (SELECT m.user_id " + CLAIM + " AND s.id=?)",String.class,device,spa);
        if (tokens.isEmpty()) throw new BookingContactUnavailable("Owner device is no longer subscribed");
        try {
            http.post().uri("https://fcm.googleapis.com/v1/projects/{project}/messages:send",project)
                    .headers(h -> h.setBearerAuth(accessTokenForRequest()))
                    .body(Map.of("message",Map.of("token",tokens.get(0),"data",Map.of("bookingId",Long.toString(booking),"event",event),
                            "webpush",Map.of("headers",Map.of("TTL","3600","Urgency","high")))))
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            JsonNode details;
            try { details=json.readTree(e.getResponseBodyAsString()).path("error").path("details"); }
            catch (IOException invalid) { throw new IllegalStateException("FCM request failed"); }
            for (JsonNode detail : details) {
                if ("UNREGISTERED".equals(detail.path("errorCode").asText())) {
                    db.update("DELETE FROM owner_push_device WHERE id=?",device);
                    throw new BookingContactUnavailable("Owner device registration expired");
                }
            }
            throw new IllegalStateException("FCM request failed: HTTP "+e.getStatusCode().value());
        }
    }

    private String accessTokenForRequest() {
        try { return accessToken(); }
        catch (IOException e) { throw new IllegalStateException("FCM authorization unavailable"); }
    }
}
