package com.nirvana.application.controller;

import com.nirvana.application.config.RazorpayProperties;
import com.nirvana.application.repository.PaymentRepository;
import com.nirvana.application.model.Payment;
import com.nirvana.application.model.enums.PaymentStatus;
import com.razorpay.Utils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@RestController
@RequestMapping("/api/payments/razorpay")
@RequiredArgsConstructor
@Slf4j
public class RazorpayWebhookController {

    private final RazorpayProperties props;
    private final PaymentRepository paymentRepository;
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
            String event = root.path("event").asText();

            switch (event) {
                case "payment.captured" -> handlePaymentCaptured(root);
                case "payment.failed" -> handlePaymentFailed(root);
                case "refund.processed" -> handleRefundProcessed(root);
                default -> log.info("Ignoring unsupported Razorpay event: {}", event);
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

        Optional<Payment> opt = paymentRepository.findByTransactionId(paymentId);
        if (opt.isEmpty()) {
            log.warn("Refund {} for unknown payment {}", refundId, paymentId);
            return;
        }

        Payment p = opt.get();
        // You can mark payment as REFUNDED/PARTIALLY_REFUNDED here and store refund info
        log.info("Webhook: refund {} processed for payment {}, amount {}", refundId, paymentId, refundAmount);
    }
}
