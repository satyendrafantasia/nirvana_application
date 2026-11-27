package com.nirvana.application.controller;

import com.nirvana.application.config.RazorpayProperties;
import com.nirvana.application.repository.PaymentRepository;
import com.nirvana.application.model.Payment;
import com.nirvana.application.model.PaymentWebhookEvent;
import com.nirvana.application.model.enums.PaymentStatus;
import com.nirvana.application.repository.PaymentWebhookEventRepository;
import com.razorpay.Utils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@RestController
@RequestMapping("/api/payments/razorpay")
@RequiredArgsConstructor
@Slf4j
public class RazorpayWebhookController {

    private final RazorpayProperties props;
    private final com.nirvana.application.service.impl.PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final com.nirvana.application.service.impl.PackageCheckoutService packageCheckoutService;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final com.nirvana.application.service.impl.SpaPackageCheckoutService spaPackageCheckoutService;
    private final com.nirvana.application.rewards.GiftCardService giftCardService;
    private final com.nirvana.application.service.impl.LoyaltyService loyaltyService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(HttpServletRequest request,
                                              @RequestBody String payload) {
        String signature = request.getHeader("X-Razorpay-Signature");
        try {
            if (signature == null || props.getWebhookSecret() == null || props.getWebhookSecret().isBlank()
                    || !Utils.verifyWebhookSignature(payload, signature, props.getWebhookSecret())) {
                return ResponseEntity.badRequest().build();
            }
        } catch (Exception e) {
            log.warn("event=payment.webhook_signature_invalid");
            return ResponseEntity.badRequest().build();
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String payloadHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            String eventId = request.getHeader("X-Razorpay-Event-Id");
            if (eventId == null || eventId.isBlank()) {
                eventId = payloadHash;
            }
            if (eventId != null && !eventId.isBlank()
                    && webhookEventRepository.findByEventId(eventId).isPresent()) {
                log.info("Skipping duplicate webhook event {}", eventId);
                return ResponseEntity.ok().build();
            }

            String event = root.path("event").asText();

            switch (event) {
                case "payment.captured" -> handlePaymentCaptured(root);
                case "payment_link.paid" -> paymentService.reconcilePaymentLink(root.path("payload").path("payment_link").path("entity").path("id").asText());
                case "payment.failed" -> handlePaymentFailed(root);
                case "refund.processed", "refund.failed", "refund.created" -> handleRefund(root, event);
                default -> log.info("Ignoring unsupported Razorpay event: {}", event);
            }
            if (eventId != null && !eventId.isBlank()) {
                PaymentWebhookEvent record = new PaymentWebhookEvent();
                record.setEventId(eventId);
                record.setGateway("razorpay");
                JsonNode body = root.path("payload");
                JsonNode refund = body.path("refund").path("entity");
                record.setPaymentId(body.path("payment").path("entity").path("id").asText(refund.path("payment_id").asText(null)));
                var metadata = objectMapper.createObjectNode().put("event", event).put("sha256", payloadHash);
                if (refund.hasNonNull("id")) metadata.put("refund_id", refund.path("id").asText());
                record.setPayload(metadata.toString());
                webhookEventRepository.save(record);
            }
        } catch (Exception e) {
            log.error("event=payment.webhook_failed errorType={}", e.getClass().getSimpleName());
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.ok().build();
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
