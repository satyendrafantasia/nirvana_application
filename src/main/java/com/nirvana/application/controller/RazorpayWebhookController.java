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
    private final PaymentRepository paymentRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(HttpServletRequest request,
                                              @RequestBody String payload) {
        String signature = request.getHeader("X-Razorpay-Signature");
        try {
            Utils.verifyWebhookSignature(
                    payload,
                    signature,
                    props.getWebhookSecret());
        } catch (Exception e) {
            log.warn("Invalid Razorpay webhook signature", e);
            return ResponseEntity.badRequest().build();
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventId = root.path("id").asText();
            if (eventId != null && !eventId.isBlank()
                    && webhookEventRepository.findByEventId(eventId).isPresent()) {
                log.info("Skipping duplicate webhook event {}", eventId);
                return ResponseEntity.ok().build();
            }

            String event = root.path("event").asText();

            switch (event) {
                case "payment.captured" -> handlePaymentCaptured(root);
                case "payment.failed" -> handlePaymentFailed(root);
                case "refund.processed" -> handleRefundProcessed(root);
                default -> log.info("Ignoring unsupported Razorpay event: {}", event);
            }
            if (eventId != null && !eventId.isBlank()) {
                PaymentWebhookEvent record = new PaymentWebhookEvent();
                record.setEventId(eventId);
                record.setGateway("razorpay");
                record.setPaymentId(root.path("payload").path("payment").path("entity").path("id").asText(null));
                record.setPayload(payload);
                webhookEventRepository.save(record);
            }
        } catch (Exception e) {
            log.error("Error processing Razorpay webhook payload", e);
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

        Optional<Payment> opt = paymentRepository.findByIntentId(orderId);
        if (opt.isEmpty()) {
            log.warn("No Payment found for order_id {} (payment {})", orderId, paymentId);
            return;
        }

        Payment p = opt.get();
        if (p.getPaymentStatus() == PaymentStatus.CAPTURED
                || p.getPaymentStatus() == PaymentStatus.COMPLETED) {
            return;
        }

        p.setTransactionId(paymentId);
        p.setPaymentStatus(PaymentStatus.CAPTURED);
        paymentRepository.save(p);

        log.info("Webhook: marked payment {} as CAPTURED for order {}", paymentId, orderId);
    }

    private void handlePaymentFailed(JsonNode root) {
        JsonNode paymentNode = root.path("payload").path("payment").path("entity");
        String paymentId = paymentNode.path("id").asText();
        String orderId = paymentNode.path("order_id").asText();

        if (orderId == null || orderId.isBlank()) {
            log.warn("payment.failed without order_id: {}", paymentId);
            return;
        }

        Optional<Payment> opt = paymentRepository.findByIntentId(orderId);
        if (opt.isEmpty()) {
            log.warn("No Payment found for order_id {} (payment {})", orderId, paymentId);
            return;
        }

        Payment p = opt.get();
        if (p.getPaymentStatus() == PaymentStatus.FAILED) {
            return;
        }

        p.setTransactionId(paymentId);
        p.setPaymentStatus(PaymentStatus.FAILED);
        paymentRepository.save(p);

        log.info("Webhook: marked payment {} as FAILED for order {}", paymentId, orderId);
    }

    private void handleRefundProcessed(JsonNode root) {
        JsonNode refundNode = root.path("payload").path("refund").path("entity");
        String refundId = refundNode.path("id").asText();
        String paymentId = refundNode.path("payment_id").asText();
        int refundAmount = refundNode.path("amount").asInt();
        long createdAt = refundNode.path("created_at").asLong(0L);
        OffsetDateTime refundTimestamp = createdAt > 0
                ? OffsetDateTime.ofInstant(Instant.ofEpochSecond(createdAt), ZoneOffset.UTC)
                : OffsetDateTime.now(ZoneOffset.UTC);

        Optional<Payment> opt = paymentRepository.findByTransactionId(paymentId);
        if (opt.isEmpty()) {
            log.warn("Refund {} for unknown payment {}", refundId, paymentId);
            return;
        }

        Payment p = opt.get();
        int updatedRefunded = Optional.ofNullable(p.getRefundedCents()).orElse(0) + refundAmount;
        p.setRefundedCents(updatedRefunded);
        p.setLastRefundId(refundId);
        p.setRefundedAt(refundTimestamp);

        if (updatedRefunded >= p.getAmountCents()) {
            p.setPaymentStatus(PaymentStatus.REFUNDED);
        } else {
            p.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
        }

        paymentRepository.save(p);

        log.info("Webhook: refund {} processed for payment {}, amount {}, total refunded {} (status {})",
                refundId, paymentId, refundAmount, updatedRefunded, p.getPaymentStatus());
    }
}
