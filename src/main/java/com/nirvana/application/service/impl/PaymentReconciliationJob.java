package com.nirvana.application.service.impl;

import com.nirvana.application.model.Payment;
import com.nirvana.application.model.enums.PaymentStatus;
import com.nirvana.application.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliationJob {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    @Value("${reliability.payments.reconcile-threshold-minutes:15}")
    private int reconcileThresholdMinutes;

    @Scheduled(fixedDelayString = "${reliability.payments.reconcile-interval-ms:300000}")
    public void markStalePayments() {
        OffsetDateTime threshold = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(reconcileThresholdMinutes);
        List<Payment> stale = new java.util.ArrayList<>(paymentRepository.findByPaymentStatus(PaymentStatus.PENDING));
        stale.addAll(paymentRepository.findByPaymentStatus(PaymentStatus.INIT));
        stale.stream()
                .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().isBefore(threshold))
                .forEach(payment -> {
                    if (!"RAZORPAY".equals(payment.getGateway())) return;
                    try { paymentService.reconcilePendingOrder(payment.getIntentId()); }
                    catch (RuntimeException ex) {log.warn("Reconciliation deferred for payment {}: {}", payment.getId(), ex.getMessage());}
                });
    }
}
