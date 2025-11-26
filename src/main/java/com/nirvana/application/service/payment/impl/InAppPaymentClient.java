package com.nirvana.application.service.payment.impl;

import com.nirvana.application.model.enums.spa.PaymentStatus;
import com.nirvana.application.service.payment.PaymentClient;
import com.nirvana.application.service.payment.PaymentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class InAppPaymentClient implements PaymentClient {

    @Override
    public PaymentResult processPayment(Long userId, Long spaId, BigDecimal amount, String paymentMethod, String paymentReference) {
        // Simulate/bridge to actual payment gateway. For now, assume success unless explicitly marked otherwise
        boolean shouldFail = "FAIL".equalsIgnoreCase(paymentMethod);
        PaymentStatus status = shouldFail ? PaymentStatus.FAILED : PaymentStatus.SUCCESS;
        String reference = paymentReference != null ? paymentReference : UUID.randomUUID().toString();
        log.info("Processed package payment for user {} spa {} amount {} with result {}", userId, spaId, amount, status);
        return new PaymentResult(status, reference);
    }
}
