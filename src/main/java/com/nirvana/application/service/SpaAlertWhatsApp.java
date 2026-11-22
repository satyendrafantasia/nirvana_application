package com.nirvana.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sends spa booking alerts on WhatsApp through Meta's Cloud API. A business may only start a
 * WhatsApp conversation with a template Meta has approved, so each alert kind has its own template;
 * the booking details fill its {{1}}, {{2}}... values and the reply token completes its URL button.
 */
@Slf4j
@Component
public class SpaAlertWhatsApp {
    private final RestTemplate http;
    private final String baseUrl;
    private final String apiVersion;
    private final String phoneNumberId;
    private final String accessToken;
    private final String language;
    private final Map<SpaAlertSms.Kind, String> templates;
    private final boolean on;

    public SpaAlertWhatsApp(RestTemplateBuilder builder,
                            @Value("${spa-alerts.channel:SMS}") String channel,
                            @Value("${spa-alerts.whatsapp.base-url:https://graph.facebook.com}") String baseUrl,
                            @Value("${spa-alerts.whatsapp.api-version:v23.0}") String apiVersion,
                            @Value("${spa-alerts.whatsapp.phone-number-id:}") String phoneNumberId,
                            @Value("${spa-alerts.whatsapp.access-token:}") String accessToken,
                            @Value("${spa-alerts.whatsapp.language:en}") String language,
                            @Value("${spa-alerts.whatsapp.templates.new:nirvana_spa_booking_new}") String newTemplate,
                            @Value("${spa-alerts.whatsapp.templates.reminder:nirvana_spa_booking_reminder}") String reminderTemplate,
                            @Value("${spa-alerts.whatsapp.templates.cancelled:nirvana_spa_booking_cancelled}") String cancelledTemplate,
                            @Value("${spa-alerts.whatsapp.templates.moved:nirvana_spa_booking_moved}") String movedTemplate) {
        this.http = builder.connectTimeout(Duration.ofSeconds(5)).readTimeout(Duration.ofSeconds(15)).build();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiVersion = apiVersion;
        this.phoneNumberId = phoneNumberId;
        this.accessToken = accessToken;
        this.language = language;
        this.templates = Map.of(SpaAlertSms.Kind.NEW, newTemplate, SpaAlertSms.Kind.REMINDER, reminderTemplate,
                SpaAlertSms.Kind.CANCELLED, cancelledTemplate, SpaAlertSms.Kind.MOVED, movedTemplate);
        this.on = "WHATSAPP".equalsIgnoreCase(channel) && StringUtils.hasText(phoneNumberId) && StringUtils.hasText(accessToken);
        if ("WHATSAPP".equalsIgnoreCase(channel) && !on)
            log.warn("Spa alerts are set to WhatsApp, but SPA_WHATSAPP_PHONE_NUMBER_ID or SPA_WHATSAPP_ACCESS_TOKEN is missing; using SMS");
    }

    /** Whether alerts go out on WhatsApp. */
    public boolean enabled() {
        return on;
    }

    /**
     * Sends one alert. {@code values} fill the template body in order; {@code linkToken}, if any,
     * completes the template's URL button (".../b/{{1}}").
     */
    public void send(String to, SpaAlertSms.Kind kind, List<String> values, String linkToken) {
        List<Object> components = new ArrayList<>();
        components.add(Map.of("type", "body", "parameters", values.stream().map(v -> Map.of("type", "text", "text", clean(v))).toList()));
        if (linkToken != null)
            components.add(Map.of("type", "button", "sub_type", "url", "index", "0",
                    "parameters", List.of(Map.of("type", "text", "text", linkToken))));
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", to.replaceAll("\\D", ""),
                "type", "template",
                "template", Map.of("name", templates.get(kind), "language", Map.of("code", language), "components", components));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        try {
            JsonNode reply = http.postForObject(baseUrl + "/" + apiVersion + "/" + phoneNumberId + "/messages",
                    new HttpEntity<>(body, headers), JsonNode.class);
            if (reply == null || !reply.path("messages").isArray() || reply.path("messages").isEmpty())
                throw new IllegalStateException("WhatsApp did not accept the " + kind + " alert");
        } catch (HttpStatusCodeException e) {
            // Meta's error body names the problem (unapproved template, wrong value count, number not on WhatsApp...).
            String why = e.getResponseBodyAsString();
            throw new IllegalStateException("WhatsApp refused the " + kind + " alert (HTTP " + e.getStatusCode().value() + "): "
                    + (why.length() > 300 ? why.substring(0, 300) : why));
        } catch (RestClientException e) {
            // Never pass on the request itself: its headers carry the access token.
            throw new IllegalStateException("Could not reach WhatsApp: " + e.getClass().getSimpleName());
        }
    }

    /** WhatsApp rejects template values with new lines, tabs or more than four spaces in a row. */
    static String clean(String value) {
        String v = value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").replaceAll(" {4,}", "   ").strip();
        return v.isEmpty() ? "-" : v;
    }
}
