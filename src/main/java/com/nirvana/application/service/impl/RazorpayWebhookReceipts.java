package com.nirvana.application.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** InnoDB serializes simultaneous deliveries on the existing unique event ID. A failed
 * reconciliation rolls back the receipt and local changes, allowing Razorpay to retry. */
@Service
@RequiredArgsConstructor
public class RazorpayWebhookReceipts {
    private final JdbcTemplate jdbc;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void processOnce(String eventId, String paymentId, String metadata, Runnable reconcile) {
        try {
            jdbc.update("""
                INSERT INTO payment_webhook_event (event_id, gateway, payment_id, payload)
                VALUES (?, 'razorpay', ?, ?)
                """, eventId, paymentId, metadata);
        } catch (DuplicateKeyException alreadyProcessed) {
            return;
        }
        reconcile.run();
    }
}
