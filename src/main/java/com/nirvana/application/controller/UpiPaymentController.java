package com.nirvana.application.controller;

import com.nirvana.application.model.dto.UpiPaymentConfirmRequest;
import com.nirvana.application.model.dto.UpiPaymentInitResponse;
import com.nirvana.application.service.impl.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings/{bookingId}/payments/upi")
@RequiredArgsConstructor
public class UpiPaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    public ResponseEntity<UpiPaymentInitResponse> initiate(@PathVariable Long bookingId) {
        return ResponseEntity.ok(paymentService.initiateUpiPayment(bookingId));
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@PathVariable Long bookingId,
                                        @Valid @RequestBody UpiPaymentConfirmRequest request) {
        paymentService.confirmUpiPayment(bookingId, request);
        return ResponseEntity.ok().build();
    }
}
