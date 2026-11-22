package com.nirvana.application.otp.sender;

import com.fasterxml.jackson.databind.JsonNode;
import com.nirvana.application.config.SmsProperties;
import com.nirvana.application.otp.exception.SmsSendException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends OTP codes through 2Factor.in, whose DLT-approved OTP templates spare us registering our own
 * texts. 2Factor sends only the code, using the named template (or its default one), so the code is
 * taken from the message the app built: the first 4-6 digit number in it. The API key is part of the
 * request path, so request URLs are never logged or passed on in errors.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sms.provider", havingValue = "TWOFACTOR")
public class TwoFactorSmsSender implements SmsSender {

    private static final Pattern CODE = Pattern.compile("(?<!\\d)\\d{4,6}(?!\\d)");

    private final RestTemplate http;
    private final SmsProperties smsProperties;
    private final String baseUrl;
    private final String apiKey;
    private final String template;

    public TwoFactorSmsSender(RestTemplateBuilder builder, SmsProperties smsProperties,
                              @Value("${sms.twofactor.base-url:https://2factor.in}") String baseUrl,
                              @Value("${sms.twofactor.api-key:}") String apiKey,
                              @Value("${sms.twofactor.otp-template:}") String template) {
        this.http = builder.connectTimeout(Duration.ofSeconds(5)).readTimeout(Duration.ofSeconds(15)).build();
        this.smsProperties = smsProperties;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.template = template;
    }

    /** The code in an OTP message, or null if it has none. */
    static String codeIn(String message) {
        Matcher m = CODE.matcher(message == null ? "" : message);
        return m.find() ? m.group() : null;
    }

    /** 2Factor wants Indian numbers as 10 digits; others keep their country code. */
    static String phoneFor2Factor(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) return digits.substring(2);
        if (digits.length() == 10) return digits;
        return "+" + digits;
    }

    @Override
    public void sendOtpSms(String phoneNumber, String message) {
        if (!smsProperties.isEnabled()) {
            log.debug("Notification delivery processed");
            return;
        }
        if (!StringUtils.hasText(apiKey)) throw new SmsSendException("2Factor is not set up: TWOFACTOR_API_KEY is missing", null);
        String code = codeIn(message);
        if (code == null) throw new SmsSendException("The OTP message has no code to send", null);

        var uri = UriComponentsBuilder.fromUriString(baseUrl).path("/API/V1/{key}/SMS/{phone}/{code}");
        if (StringUtils.hasText(template)) uri.path("/{template}");
        var url = StringUtils.hasText(template)
                ? uri.buildAndExpand(apiKey, phoneFor2Factor(phoneNumber), code, template).encode().toUri()
                : uri.buildAndExpand(apiKey, phoneFor2Factor(phoneNumber), code).encode().toUri();
        try {
            JsonNode reply = http.getForObject(url, JsonNode.class);
            if (reply == null || !"Success".equalsIgnoreCase(reply.path("Status").asText()))
                throw new SmsSendException("2Factor did not send the OTP: " + (reply == null ? "empty reply" : reply.path("Details").asText()), null);
            log.debug("Notification delivery processed");
        } catch (HttpStatusCodeException ex) {
            log.error("Notification delivery failed");
            throw new SmsSendException("2Factor did not send the OTP (HTTP " + ex.getStatusCode().value() + "): " + details(ex), null);
        } catch (RestClientException ex) {
            // The exception message would contain the URL, and with it the API key.
            log.error("Notification delivery failed");
            throw new SmsSendException("Could not reach 2Factor: " + ex.getClass().getSimpleName(), null);
        }
    }

    private static String details(HttpStatusCodeException ex) {
        String body = ex.getResponseBodyAsString();
        Matcher m = Pattern.compile("\"Details\"\\s*:\\s*\"([^\"]{0,200})\"").matcher(body);
        return m.find() ? m.group(1) : "no details";
    }
}
