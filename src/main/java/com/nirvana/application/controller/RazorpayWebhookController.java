package com.nirvana.application.controller;

import com.nirvana.application.config.RazorpayProperties;
import com.nirvana.application.service.impl.RazorpayWebhookReceipts;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments/razorpay")
@RequiredArgsConstructor
@Slf4j
public class RazorpayWebhookController {

    private final RazorpayProperties props;
    private final com.nirvana.application.service.impl.PaymentService paymentService;
    private final com.nirvana.application.service.impl.PackageCheckoutService packageCheckoutService;
    private final RazorpayWebhookReceipts receipts;
    private final com.nirvana.application.service.impl.SpaPackageCheckoutService spaPackageCheckoutService;
    private final com.nirvana.application.rewards.GiftCardService giftCardService;
    private final com.nirvana.application.service.impl.LoyaltyService loyaltyService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(HttpServletRequest request,
                                              @RequestBody byte[] payload) {
        String signature = request.getHeader("X-Razorpay-Signature");
        String secret = props.getWebhookSecret();
        if (secret == null || secret.isBlank() || signature == null || !signature.matches("[0-9a-fA-F]{64}")) {
            return ResponseEntity.badRequest().build();
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            if (!MessageDigest.isEqual(mac.doFinal(payload), HexFormat.of().parseHex(signature))) {
                return ResponseEntity.badRequest().build();
            }
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
            String eventId = request.getHeader("X-Razorpay-Event-Id");
            if (eventId == null || eventId.isBlank()) eventId = hash;
            if (eventId.length() > 255) return ResponseEntity.badRequest().build();
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || !root.path("event").isTextual()) return ResponseEntity.badRequest().build();
            String event = root.get("event").textValue();
            JsonNode body = root.path("payload");
            JsonNode refund = body.path("refund").path("entity");
            String paymentId = body.path("payment").path("entity").path("id").asText(refund.path("payment_id").asText(null));
            var metadata = objectMapper.createObjectNode().put("event", event).put("sha256", hash);
            if (refund.hasNonNull("id")) metadata.put("refund_id", refund.get("id").asText());
            receipts.processOnce(eventId, paymentId, metadata.toString(), () -> dispatch(root, event));
            return ResponseEntity.ok().build();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("event=payment.webhook_failed errorType={}", e.getClass().getSimpleName());
            return ResponseEntity.internalServerError().build();
        }
    }

    private void dispatch(JsonNode root, String event) {
        switch (event) {
            case "payment.captured" -> handlePaymentCaptured(root);
            case "payment_link.paid" -> paymentService.reconcilePaymentLink(root.path("payload").path("payment_link").path("entity").path("id").asText());
            case "payment.failed" -> handlePaymentFailed(root);
            case "refund.processed", "refund.failed", "refund.created" -> handleRefund(root, event);
            default -> log.info("event=payment.webhook_unsupported");
        }
    }

    private void handlePaymentCaptured(JsonNode root) {
        JsonNode paymentNode = root.path("payload").path("payment").path("entity");
        String paymentId = paymentNode.path("id").asText();
        String orderId = paymentNode.path("order_id").asText();

        if (orderId == null || orderId.isBlank()) {
            log.warn("payment.captured without order_id: {}", paymentId);
            return;
        }

        paymentService.reconcileCapturedPayment(orderId, paymentId);
        packageCheckoutService.captured(orderId, paymentId);
        spaPackageCheckoutService.captured(orderId, paymentId);
        giftCardService.captured(orderId, paymentId);
    }

    private void handlePaymentFailed(JsonNode root) {
        JsonNode payment = root.path("payload").path("payment").path("entity");
        paymentService.reconcileFailedPayment(payment.path("order_id").asText(), payment.path("id").asText());
    }

    private void handleRefund(JsonNode root, String event) {
        JsonNode refund = root.path("payload").path("refund").path("entity");
        paymentService.reconcileRefund(refund.path("payment_id").asText(), refund.path("id").asText());
        if (!"refund.processed".equals(event)) return;
        spaPackageCheckoutService.refunded(root.path("payload").path("refund").path("entity").path("payment_id").asText());
        packageCheckoutService.refunded(root.path("payload").path("refund").path("entity").path("payment_id").asText());
        giftCardService.refunded(root.path("payload").path("refund").path("entity").path("payment_id").asText());
        // Points earned on a completed booking follow the money: a refund takes its share back.
        loyaltyService.syncEarnedForPayment(root.path("payload").path("refund").path("entity").path("payment_id").asText());
    }
}
