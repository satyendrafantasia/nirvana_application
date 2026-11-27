package com.nirvana.application.service.impl;

import com.nirvana.application.repository.PaymentRefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingRefundJob {
    private final PaymentRefundRepository cases;
    private final BookingRefundRecovery recovery;

    @Scheduled(fixedDelayString = "${reliability.payments.refund-interval-ms:30000}")
    public void reconcile() {
        for (Long id : cases.findDue(OffsetDateTime.now(ZoneOffset.UTC), PageRequest.of(0, 25))) {
            try {
                recovery.dispatch(id);
            } catch (RuntimeException ex) {
                log.warn("event=refund.recovery_deferred refundId={} errorType={}", id, ex.getClass().getSimpleName());
                boolean needsReview = ex instanceof org.springframework.web.client.HttpClientErrorException clientError
                        && clientError.getStatusCode().value() != 409 && clientError.getStatusCode().value() != 429;
                recovery.defer(id, needsReview);
            }
        }
    }
}
