// src/main/java/com/nirvana/application/controller/PaymentController.java
package com.nirvana.application.controller;

import com.nirvana.application.model.dto.PaymentInitResponse;
import com.nirvana.application.model.dto.PaymentLinkInitResponse;
import com.nirvana.application.model.dto.RazorpayConfirmRequest;
import com.nirvana.application.service.impl.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings/{bookingId}/payments/razorpay")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Step 1 — Create Razorpay order.
     * POST /api/bookings/{bookingId}/payments/razorpay/order
     */
    @PostMapping("/order")
    public ResponseEntity<PaymentInitResponse> createOrder(
            @PathVariable Long bookingId) {
        return ResponseEntity.ok(paymentService.initiateRazorpayPayment(bookingId));
    }

    /**
     * Step 2 — Confirm Razorpay payment.
     * POST /api/bookings/{bookingId}/payments/razorpay/confirm
     */
    @PostMapping("/confirm")
    public ResponseEntity<Void> confirmPayment(
            @PathVariable Long bookingId,
            @Valid @RequestBody RazorpayConfirmRequest request) {

        paymentService.confirmRazorpayPayment(bookingId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * Retry a payment — regenerate order_id.
     * POST /api/bookings/{bookingId}/payments/razorpay/retry
     */
    @PostMapping("/retry")
    public ResponseEntity<PaymentInitResponse> retry(
            @PathVariable Long bookingId) {

        return ResponseEntity.ok(paymentService.retryRazorpayPayment(bookingId));
    }

    /**
     * Create a Razorpay Payment Link as fallback.
     * POST /api/bookings/{bookingId}/payments/razorpay/payment-link
     */
    @PostMapping("/payment-link")
    public ResponseEntity<PaymentLinkInitResponse> createPaymentLink(
            @PathVariable Long bookingId) {

        return ResponseEntity.ok(paymentService.createPaymentLinkForBooking(bookingId));
    }
}
