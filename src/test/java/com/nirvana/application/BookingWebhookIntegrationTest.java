package com.nirvana.application;

import com.nirvana.application.model.Payment;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.PaymentMode;
import com.razorpay.PaymentClient;
import com.razorpay.RazorpayClient;
import jakarta.persistence.EntityManager;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "payments.razorpay.enabled=true", "payments.razorpay.key-id=fixture",
        "payments.razorpay.key-secret=fixture-key", "payments.razorpay.webhook-secret=fixture-webhook",
        "app.scheduling.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class BookingWebhookIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate db;
    @MockitoBean RazorpayClient gateway;

    @Test void onlyAuthenticatedCapturedPaymentConfirmsAndQueuesOneCustomerAlert() throws Exception {
        var booking = BookingFixtures.payAtSpa(em, OffsetDateTime.now().plusDays(3), BookingStatus.PENDING_PAYMENT);
        booking.setPaymentMode(PaymentMode.ONLINE);
        String suffix = UUID.randomUUID().toString();
        String order = "order_" + suffix, tx = "pay_" + suffix, event = "evt_" + suffix;
        var payment = new Payment();
        payment.setBooking(booking);
        payment.setGateway("RAZORPAY");
        payment.setIntentId(order);
        payment.setAmountCents(booking.getPriceCents());
        payment.setTotalPrice(BigDecimal.valueOf(booking.getPriceCents(), 2));
        payment.setReceiptUrl("/existing-receipt");
        em.persist(payment);
        em.flush();

        var captured = new JSONObject().put("id", tx).put("order_id", order)
                .put("amount", payment.getAmountCents()).put("currency", "INR")
                .put("captured", true).put("status", "captured").put("amount_refunded", 0);
        gateway.payments = mock(PaymentClient.class);
        when(gateway.payments.fetch(tx)).thenReturn(new com.razorpay.Payment(captured));
        byte[] body = new JSONObject().put("event", "payment.captured").put("payload",
                new JSONObject().put("payment", new JSONObject().put("entity", captured)))
                .toString().getBytes(StandardCharsets.UTF_8);
        Mac signer = Mac.getInstance("HmacSHA256");
        signer.init(new SecretKeySpec("fixture-webhook".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(signer.doFinal(body));
        send(body, event, "0".repeat(64), 400);
        assertBookingState(booking.getId(), event, "PENDING_PAYMENT", "INIT", 0);
        verifyNoInteractions(gateway.payments);

        send(body, event, signature, 200);
        assertBookingState(booking.getId(), event, "CONFIRMED", "CAPTURED", 1);
        send(body, event, signature, 200);
        assertBookingState(booking.getId(), event, "CONFIRMED", "CAPTURED", 1);
        verify(gateway.payments, times(1)).fetch(tx);
    }

    private void send(byte[] body, String event, String signature, int expected) throws Exception {
        mvc.perform(post("/api/payments/razorpay/webhook").contentType("application/json").content(body)
                        .header("X-Razorpay-Event-Id", event).header("X-Razorpay-Signature", signature))
                .andExpect(status().is(expected));
    }

    private void assertBookingState(long booking, String event, String status, String paid, int effects) {
        em.flush();
        assertEquals(status, db.queryForObject("SELECT status FROM booking WHERE id=?", String.class, booking));
        assertEquals(paid, db.queryForObject("SELECT payment_status FROM payment WHERE booking_id=?", String.class, booking));
        assertEquals(effects, db.queryForObject("SELECT COUNT(*) FROM payment_webhook_event WHERE event_id=?", Integer.class, event));
        assertEquals(effects, db.queryForObject("""
                SELECT COUNT(*) FROM booking_alert_job
                WHERE booking_id=? AND event_kind='CONFIRMED' AND audience='CUSTOMER_EMAIL'
                """, Integer.class, booking));
    }
}
