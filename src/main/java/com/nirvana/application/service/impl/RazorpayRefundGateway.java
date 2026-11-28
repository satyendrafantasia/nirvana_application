package com.nirvana.application.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.nirvana.application.config.RazorpayProperties;
import com.nirvana.application.model.Payment;
import com.nirvana.application.model.PaymentRefund;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

@Component
public class RazorpayRefundGateway {
    private final RestClient client;
    private final RazorpayProperties properties;

    @org.springframework.beans.factory.annotation.Autowired
    public RazorpayRefundGateway(RazorpayProperties properties) {
        this(properties, transport());
    }

    private static RestClient.Builder transport() {
        var transport = new SimpleClientHttpRequestFactory();
        transport.setConnectTimeout(Duration.ofSeconds(4));
        transport.setReadTimeout(Duration.ofSeconds(8));
        return RestClient.builder().requestFactory(transport);
    }

    RazorpayRefundGateway(RazorpayProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        client = builder.baseUrl("https://api.razorpay.com/v1")
                .defaultHeaders(h -> {
                    if (properties.credentialsConfigured()) h.setBasicAuth(properties.getKeyId(), properties.getKeySecret());
                }).build();
    }

    static String receipt(PaymentRefund refund) {
        return "nirvana-" + refund.getId() + "-" + refund.getGatewayAttempt();
    }

    JsonNode submit(PaymentRefund refund) {
        requireCredentials();
        String paymentId = refund.getPayment().gatewayPaymentId();
        String receipt = receipt(refund);
        return client.post().uri("/payments/{payment}/refund", paymentId)
                .header("X-Refund-Idempotency", paymentId + "-" + receipt)
                .body(Map.of("amount", refund.getAmountCents(), "receipt", receipt))
                .retrieve().body(JsonNode.class);
    }

    JsonNode fetch(String paymentId, String refundId) {
        requireCredentials();
        return client.get().uri("/payments/{payment}/refunds/{refund}", paymentId, refundId)
                .retrieve().body(JsonNode.class);
    }

    // A payment's amount_refunded can include pending requests. Count only settled refunds.
    int processedAmount(Payment payment, String requiredRefundId) {
        requireCredentials();
        int total = 0;
        boolean found = false;
        var seen = new java.util.HashSet<String>();
        String paymentId = payment.getTransactionId();
        for (int skip = 0; ; skip += 100) {
            JsonNode page = client.get().uri("/payments/{payment}/refunds?count=100&skip={skip}", paymentId, skip)
                    .retrieve().body(JsonNode.class);
            if (page == null || !page.path("items").isArray()) throw new IllegalStateException("Missing refund list");
            for (JsonNode row : page.path("items")) {
                if (!paymentId.equals(row.path("payment_id").asText())
                        || !payment.getCurrency().equalsIgnoreCase(row.path("currency").asText())
                        || row.path("amount").asInt() <= 0) throw new IllegalStateException("Refund payment mismatch");
                String refundId = row.path("id").asText();
                if (refundId.isBlank()) throw new IllegalStateException("Missing provider refund ID");
                if ("processed".equals(row.path("status").asText()) && seen.add(refundId)) {
                    total = Math.addExact(total, row.path("amount").intValue());
                    if (requiredRefundId.equals(row.path("id").asText())) found = true;
                }
            }
            if (total > payment.getAmountCents()) throw new IllegalStateException("Refunds exceed captured amount");
            if (page.path("items").size() < 100) {
                if (!found) throw new IllegalStateException("Processed refund not yet visible in the provider ledger");
                return total;
            }
        }
    }

    void verifyNeverRefunded(Payment payment) {
        requireCredentials();
        String paymentId = payment.getTransactionId();
        JsonNode paid = client.get().uri("/payments/{id}", paymentId).retrieve().body(JsonNode.class);
        if (paid == null || !paymentId.equals(paid.path("id").asText()) || !paid.path("captured").asBoolean()
                || !"captured".equals(paid.path("status").asText()) || paid.path("amount_refunded").asInt(-1) != 0
                || !payment.getCurrency().equalsIgnoreCase(paid.path("currency").asText())
                || paid.path("amount").asInt(-1) != payment.getAmountCents())
            throw new IllegalStateException("The captured payment must be verified in Razorpay before recovery");
        JsonNode page = client.get().uri("/payments/{id}/refunds?count=1", paymentId).retrieve().body(JsonNode.class);
        if (page == null || !page.path("items").isArray() || !page.path("items").isEmpty())
            throw new IllegalStateException("This payment has a provider refund. Verify its refund ID instead.");
    }

    private void requireCredentials() {
        if (!properties.credentialsConfigured()) throw new IllegalStateException("Razorpay credentials unavailable");
    }
}
