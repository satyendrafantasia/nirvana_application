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
                    payment.setPaymentStatus(PaymentStatus.FAILED);
                    payment.setMetaJson("{\"reason\":\"stale-unconfirmed\"}");
                    paymentRepository.save(payment);
                    log.warn("Reconciliation marked payment {} as FAILED after {} minutes without capture",
                            payment.getId(), reconcileThresholdMinutes);
                });
    }
}
