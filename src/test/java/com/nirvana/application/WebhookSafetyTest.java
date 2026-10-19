package com.nirvana.application;
import com.nirvana.application.controller.RazorpayWebhookController;
import com.nirvana.application.config.RazorpayProperties;
import com.nirvana.application.repository.*;
import com.nirvana.application.service.impl.PaymentService;
import com.nirvana.application.model.PaymentWebhookEvent;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebhookSafetyTest {
    @Test void invalidSignatureCannotMutatePayments(){
        var props=new RazorpayProperties();props.setWebhookSecret("secret");
        var service=mock(PaymentService.class);var payments=mock(PaymentRepository.class);var events=mock(PaymentWebhookEventRepository.class);
        var controller=new RazorpayWebhookController(props,service,payments,mock(com.nirvana.application.service.impl.PackageCheckoutService.class),events,mock(com.nirvana.application.service.impl.SpaPackageCheckoutService.class));
        var request=new MockHttpServletRequest();request.addHeader("X-Razorpay-Signature","invalid");
        assertEquals(400,controller.handleWebhook(request,"{}").getStatusCode().value());verifyNoInteractions(service,payments,events);
    }
    @Test void authenticatedCaptureUsesSharedVerificationAndHeaderEventId() throws Exception {
        var props=new RazorpayProperties();props.setWebhookSecret("secret");
        var service=mock(PaymentService.class);var payments=mock(PaymentRepository.class);var events=mock(PaymentWebhookEventRepository.class);
        var controller=new RazorpayWebhookController(props,service,payments,mock(com.nirvana.application.service.impl.PackageCheckoutService.class),events,mock(com.nirvana.application.service.impl.SpaPackageCheckoutService.class));
        String payload="{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_1\",\"order_id\":\"order_1\"}}}}";
        var request=new MockHttpServletRequest();request.addHeader("X-Razorpay-Event-Id","evt_1");
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));request.addHeader("X-Razorpay-Signature",HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))));
        assertEquals(200,controller.handleWebhook(request,payload).getStatusCode().value());
        verify(service).reconcileCapturedPayment("order_1","pay_1");
        verify(events).save(argThat(e->"evt_1".equals(e.getEventId())));
        when(events.findByEventId("evt_1")).thenReturn(Optional.of(new PaymentWebhookEvent()));
        assertEquals(200,controller.handleWebhook(request,payload).getStatusCode().value());
        verify(service,times(1)).reconcileCapturedPayment(anyString(),anyString());
    }
}
