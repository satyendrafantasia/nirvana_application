package com.nirvana.application;

import com.nirvana.application.config.RazorpayProperties;
import com.nirvana.application.controller.RazorpayWebhookController;
import com.nirvana.application.rewards.GiftCardService;
import com.nirvana.application.service.impl.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebhookSafetyTest {
    static final String SECRET = "unit-fixture-secret";
    static MockHttpServletRequest signed(byte[] body) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        var request = new MockHttpServletRequest();
        request.addHeader("X-Razorpay-Signature", HexFormat.of().formatHex(hmac.doFinal(body)));
        return request;
    }

    static RazorpayWebhookController controller(PaymentService payments, RazorpayWebhookReceipts receipts) {
        var props = new RazorpayProperties();
        props.setWebhookSecret(SECRET);
        return new RazorpayWebhookController(props, payments, mock(PackageCheckoutService.class), receipts,
                mock(SpaPackageCheckoutService.class), mock(GiftCardService.class), mock(LoyaltyService.class));
    }

    @Test void authenticatesOriginalBytesBeforeParsingOrWriting() throws Exception {
        var payments = mock(PaymentService.class);
        var receipts = mock(RazorpayWebhookReceipts.class);
        var controller = controller(payments, receipts);
        byte[] original = "{  \"event\":\"unknown\",\"note\":\"\u20b9\" }".getBytes(StandardCharsets.UTF_8);
        byte[] changed = new String(original, StandardCharsets.UTF_8).replace("  ", " ").getBytes(StandardCharsets.UTF_8);
        assertEquals(400, controller.handleWebhook(signed(original), changed).getStatusCode().value());
        verifyNoInteractions(receipts, payments);
        var request = signed(original);
        request.addHeader("X-Razorpay-Event-Id", "evt_local");
        assertEquals(200, controller.handleWebhook(request, original).getStatusCode().value());
        verify(receipts).processOnce(eq("evt_local"), isNull(), argThat(x -> x.contains("sha256") && !x.contains("note")), any());
    }
}
