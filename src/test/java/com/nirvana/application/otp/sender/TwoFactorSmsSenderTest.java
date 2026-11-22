package com.nirvana.application.otp.sender;

import com.nirvana.application.config.SmsProperties;
import com.nirvana.application.otp.exception.SmsSendException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.client.RestTemplateBuilder;
import software.amazon.awssdk.services.sns.SnsClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** OTP codes sent through 2Factor.in: the request it gets, and what happens when it refuses. */
class TwoFactorSmsSenderTest {
    private static final String KEY = "test-key-0000";
    private HttpServer server;
    private final List<String> paths = new ArrayList<>();
    private int status = 200;
    private String reply = "{\"Status\":\"Success\",\"Details\":\"session-1\"}";

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            paths.add(ex.getRequestURI().getRawPath());
            byte[] body = reply.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
    }

    @AfterEach void stop() { server.stop(0); }

    private TwoFactorSmsSender sender(boolean enabled, String template) {
        SmsProperties props = new SmsProperties();
        props.setEnabled(enabled);
        return new TwoFactorSmsSender(new RestTemplateBuilder(), props,
                "http://127.0.0.1:" + server.getAddress().getPort(), KEY, template);
    }

    @Test void sendsTheCodeWithTheTemplateToATenDigitNumber() {
        sender(true, "NirvanaOTP").sendOtpSms("+919876543210", "Your OTP is 482913. It is valid for 10 minutes.");
        assertEquals(List.of("/API/V1/" + KEY + "/SMS/9876543210/482913/NirvanaOTP"), paths);
    }

    @Test void usesTheDefaultTemplateWhenNoneIsSet() {
        sender(true, "").sendOtpSms("+919876543210", "Your OTP is 482913. It is valid for 10 minutes.");
        assertEquals(List.of("/API/V1/" + KEY + "/SMS/9876543210/482913"), paths);
    }

    @Test void findsTheCodeInEveryOtpMessage() {
        assertEquals("482913", TwoFactorSmsSender.codeIn("Your OTP is 482913. It is valid for 10 minutes."));
        assertEquals("120587", TwoFactorSmsSender.codeIn("Nirvana: 120587 is your code to claim Spa 21 on Nirvana. It expires in 10 minutes."));
        assertNull(TwoFactorSmsSender.codeIn("No code here, valid for 10 minutes."));
        assertEquals("9876543210", TwoFactorSmsSender.phoneFor2Factor("+91 98765 43210"));
        assertEquals("+447700900123", TwoFactorSmsSender.phoneFor2Factor("+44 7700 900123"));
    }

    @Test void aRefusalSaysWhyWithoutTheApiKey() {
        status = 400;
        reply = "{\"Status\":\"Error\",\"Details\":\"Invalid Phone Number - Length Mismatch(Expected: 10)\"}";
        var e = assertThrows(SmsSendException.class,
                () -> sender(true, "").sendOtpSms("+919876543210", "Your OTP is 482913."));
        assertThat(e.getMessage()).contains("HTTP 400", "Length Mismatch").doesNotContain(KEY);
        assertNull(e.getCause());
    }

    @Test void anErrorStatusInASuccessfulReplyIsStillAFailure() {
        reply = "{\"Status\":\"Error\",\"Details\":\"Invalid API Key\"}";
        var e = assertThrows(SmsSendException.class,
                () -> sender(true, "").sendOtpSms("+919876543210", "Your OTP is 482913."));
        assertThat(e.getMessage()).contains("Invalid API Key");
    }

    @Test void anUnreachableServerDoesNotLeakTheKey() {
        server.stop(0);
        var e = assertThrows(SmsSendException.class,
                () -> sender(true, "").sendOtpSms("+919876543210", "Your OTP is 482913."));
        assertThat(e.getMessage()).doesNotContain(KEY);
        assertNull(e.getCause());
    }

    @Test void sendsNothingWhileSmsIsOff() {
        sender(false, "").sendOtpSms("+919876543210", "Your OTP is 482913.");
        assertTrue(paths.isEmpty());
    }

    @Test void theProviderSettingPicksOneSender() {
        var runner = new ApplicationContextRunner()
                .withBean(SmsProperties.class, SmsProperties::new)
                .withBean(SnsClient.class, () -> mock(SnsClient.class))
                .withBean(RestTemplateBuilder.class, RestTemplateBuilder::new)
                .withUserConfiguration(SnsSmsSender.class, TwoFactorSmsSender.class);
        runner.run(ctx -> assertThat(ctx).getBean(SmsSender.class).isInstanceOf(SnsSmsSender.class));
        runner.withPropertyValues("sms.provider=TWOFACTOR")
                .run(ctx -> assertThat(ctx).getBean(SmsSender.class).isInstanceOf(TwoFactorSmsSender.class));
    }
}
