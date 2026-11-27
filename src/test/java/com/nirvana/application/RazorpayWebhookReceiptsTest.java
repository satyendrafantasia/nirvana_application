package com.nirvana.application;

import com.nirvana.application.service.impl.RazorpayWebhookReceipts;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RazorpayWebhookReceiptsTest {
    @Autowired RazorpayWebhookReceipts receipts;
    @Autowired JdbcTemplate jdbc;

    @Test void simultaneousDeliveriesDispatchOnceAndFailedDeliveryRemainsRetryable() throws Exception {
        String event = "unit-" + UUID.randomUUID();
        var count = new AtomicInteger();
        var gate = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            assertThrows(IllegalStateException.class, () -> receipts.processOnce(event, null, "{}", () -> {
                throw new IllegalStateException("fixture gateway temporarily unavailable");
            }));
            assertEquals(0, receiptCount(event));
            Callable<Void> delivery = () -> {
                gate.await();
                receipts.processOnce(event, null, "{}", count::incrementAndGet);
                return null;
            };
            var first = pool.submit(delivery);
            var second = pool.submit(delivery);
            gate.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
            assertEquals(1, count.get());
            assertEquals(1, receiptCount(event));
        } finally {
            pool.shutdownNow();
            jdbc.update("DELETE FROM payment_webhook_event WHERE event_id=?", event);
        }
    }

    private int receiptCount(String event) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM payment_webhook_event WHERE event_id=?", Integer.class, event);
    }
}
