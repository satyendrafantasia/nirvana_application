// src/main/java/com/nirvana/application/controller/PaymentController.java
package com.nirvana.application.controller;

import com.nirvana.application.model.dto.PaymentInitResponse;
import com.nirvana.application.model.dto.PaymentLinkInitResponse;
import com.nirvana.application.model.dto.RazorpayConfirmRequest;
import com.nirvana.application.service.impl.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings/{bookingId}/payments/razorpay")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "Payment initiation, confirmation and retries for bookings")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Step 1 — Create Razorpay order.
     * POST /api/bookings/{bookingId}/payments/razorpay/order
     */
    @PostMapping("/order")
    @Operation(summary = "Initiate Razorpay order", description = "Generate a Razorpay order for the specified booking to start checkout.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order created", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentInitResponse.class)))
    })
    public ResponseEntity<PaymentInitResponse> createOrder(
            @Parameter(description = "Booking identifier") @PathVariable Long bookingId) {
        return ResponseEntity.ok(paymentService.initiateRazorpayPayment(bookingId));
    }

    /**
     * Step 2 — Confirm Razorpay payment.
     * POST /api/bookings/{bookingId}/payments/razorpay/confirm
     */
    @PostMapping("/confirm")
    @Operation(summary = "Confirm Razorpay payment", description = "Validate payment signature and mark booking as paid.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment confirmed"),
            @ApiResponse(responseCode = "400", description = "Invalid confirmation", content = @Content(schema = @Schema(implementation = com.nirvana.application.api.ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> confirmPayment(
            @Parameter(description = "Booking identifier") @PathVariable Long bookingId,
            @Valid @RequestBody RazorpayConfirmRequest request) {

        paymentService.confirmRazorpayPayment(bookingId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * Retry a payment — regenerate order_id.
     * POST /api/bookings/{bookingId}/payments/razorpay/retry
     */
    @PostMapping("/retry")
    @Operation(summary = "Retry payment", description = "Regenerate Razorpay order for a booking that failed payment.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Retry order created", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentInitResponse.class)))
    })
    public ResponseEntity<PaymentInitResponse> retry(
            @Parameter(description = "Booking identifier") @PathVariable Long bookingId) {

        return ResponseEntity.ok(paymentService.retryRazorpayPayment(bookingId));
    }

    /**
     * Create a Razorpay Payment Link as fallback.
     * POST /api/bookings/{bookingId}/payments/razorpay/payment-link
     */
    @PostMapping("/payment-link")
    @Operation(summary = "Create payment link", description = "Generate a Razorpay payment link as a fallback for the booking.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment link created", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentLinkInitResponse.class)))
    })
    public ResponseEntity<PaymentLinkInitResponse> createPaymentLink(
            @Parameter(description = "Booking identifier") @PathVariable Long bookingId) {

        return ResponseEntity.ok(paymentService.createPaymentLinkForBooking(bookingId));
    }
}
