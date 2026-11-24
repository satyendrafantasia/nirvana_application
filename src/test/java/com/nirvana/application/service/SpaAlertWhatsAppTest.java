package com.nirvana.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.model.Booking;
import com.nirvana.application.model.Spa;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Spa booking alerts on WhatsApp: the template request Meta gets, and the fall back to SMS. */
class SpaAlertWhatsAppTest {
    private static final String TOKEN = "meta-access-token";
    private HttpServer server;
    private final List<String> paths = new ArrayList<>();
    private final List<String> auth = new ArrayList<>();
    private final List<JsonNode> bodies = new ArrayList<>();
    private int status = 200;
    private String reply = "{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.1\"}]}";

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            paths.add(ex.getRequestURI().getPath());
            auth.add(ex.getRequestHeaders().getFirst("Authorization"));
            bodies.add(new ObjectMapper().readTree(ex.getRequestBody()));
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
    }

    @AfterEach void stop() { server.stop(0); }

    private SpaAlertWhatsApp whatsApp(String channel, String phoneNumberId) {
        return new SpaAlertWhatsApp(new RestTemplateBuilder(), channel, "http://127.0.0.1:" + server.getAddress().getPort(),
                "v23.0", phoneNumberId, TOKEN, "en", "nirvana_spa_booking_new", "nirvana_spa_booking_reminder",
                "nirvana_spa_booking_cancelled", "nirvana_spa_booking_moved");
    }

    @Test void sendsTheApprovedTemplateWithTheBookingAndTheReplyLink() {
        whatsApp("WHATSAPP", "1234567890").send("+919876543210", SpaAlertSms.Kind.NEW,
                List.of("7451CD21648D", "Swedish Massage, Tue 25 Nov, 7:00 PM, 1 guest, paid online", "5:30 PM"), "tok_abc");
        assertEquals(List.of("/v23.0/1234567890/messages"), paths);
        assertEquals("Bearer " + TOKEN, auth.get(0));
        JsonNode body = bodies.get(0);
        assertEquals("919876543210", body.path("to").asText());
        assertEquals("template", body.path("type").asText());
        assertEquals("nirvana_spa_booking_new", body.at("/template/name").asText());
        assertEquals("en", body.at("/template/language/code").asText());
        assertEquals("7451CD21648D", body.at("/template/components/0/parameters/0/text").asText());
        assertEquals("5:30 PM", body.at("/template/components/0/parameters/2/text").asText());
        assertEquals("url", body.at("/template/components/1/sub_type").asText());
        assertEquals("tok_abc", body.at("/template/components/1/parameters/0/text").asText());
    }

    @Test void aCancellationHasNoButton() {
        whatsApp("WHATSAPP", "1").send("+919876543210", SpaAlertSms.Kind.CANCELLED, List.of("REF", "Tue 25 Nov, 7:00 PM"), null);
        assertEquals("nirvana_spa_booking_cancelled", bodies.get(0).at("/template/name").asText());
        assertEquals(1, bodies.get(0).at("/template/components").size());
    }

    @Test void metasRefusalIsExplainedWithoutTheToken() {
        status = 400;
        reply = "{\"error\":{\"message\":\"Template name does not exist in the translation\",\"code\":132001}}";
        var e = assertThrows(IllegalStateException.class, () -> whatsApp("WHATSAPP", "1")
                .send("+919876543210", SpaAlertSms.Kind.NEW, List.of("a", "b", "c"), "t"));
        assertThat(e.getMessage()).contains("HTTP 400", "does not exist").doesNotContain(TOKEN);
    }

    @Test void itIsOnlyOnWhenChosenAndSetUp() {
        assertTrue(whatsApp("WHATSAPP", "1").enabled());
        assertFalse(whatsApp("SMS", "1").enabled());
        assertFalse(whatsApp("WHATSAPP", "").enabled());
    }

    @Test void storedTimesReadTheSameWhicheverTypeTheDriverReturns() {
        var at = java.time.Instant.parse("2026-11-25T13:30:00Z");
        assertEquals(at, SpaBookingAlerts.fromUtc(java.sql.Timestamp.valueOf("2026-11-25 13:30:00")));
        assertEquals(at, SpaBookingAlerts.fromUtc(java.time.LocalDateTime.parse("2026-11-25T13:30:00")));
        assertThrows(IllegalArgumentException.class, () -> SpaBookingAlerts.fromUtc(null));
    }

    @Test void templateValuesHaveNoLineBreaksOrLongGaps() {
        assertEquals("Spa A Line two", SpaAlertWhatsApp.clean("Spa A\nLine two"));
        assertEquals("a   b", SpaAlertWhatsApp.clean("a        b"));
        assertEquals("-", SpaAlertWhatsApp.clean("  "));
    }

    @Test void whenWhatsAppFailsTheSpaStillGetsTheSms() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), any())).thenReturn(List.of("+919876543210"));
        SpaAlertSms sms = mock(SpaAlertSms.class);
        SpaAlertWhatsApp whatsApp = mock(SpaAlertWhatsApp.class);
        when(whatsApp.enabled()).thenReturn(true);
        doThrow(new IllegalStateException("WhatsApp refused")).when(whatsApp).send(anyString(), any(), anyList(), any());
        SpaBookingAlerts alerts = new SpaBookingAlerts(jdbc, sms, whatsApp);
        ReflectionTestUtils.setField(alerts, "enabled", true);
        ReflectionTestUtils.setField(alerts, "frontendUrl", "https://nirvana-spa.in");

        Spa spa = new Spa();
        spa.setTimezone("Asia/Kolkata");
        Booking b = new Booking();
        b.setId(9L);
        b.setBookingReference("REF123");
        b.setSpa(spa);
        b.setStartTs(OffsetDateTime.parse("2026-11-25T13:30:00Z"));
        alerts.bookingCancelled(b);

        verify(whatsApp).send(eq("+919876543210"), eq(SpaAlertSms.Kind.CANCELLED), eq(List.of("REF123", "Wed 25 Nov, 7:00 PM")), isNull());
        verify(sms).send(eq("+919876543210"), eq(SpaAlertSms.Kind.CANCELLED), contains("REF123"));
    }
}
