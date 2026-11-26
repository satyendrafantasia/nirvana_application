package com.nirvana.application;

import com.nirvana.application.service.gst.GstDocument;
import com.nirvana.application.service.gst.GstInvoiceService;
import com.nirvana.application.service.gst.GstNumbering;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Document numbers under real concurrency: each issue commits in its own transaction on its own
 * thread, as two requests would. Not @Transactional, so everything made here is removed at the end.
 */
@SpringBootTest(properties = {GstInvoiceTest.NIRVANA, "gst.sweep.lookback-days=0"})
class GstNumberingConcurrencyTest {
    @Autowired GstNumbering numbering;
    @Autowired GstInvoiceService gst;
    @Autowired JdbcTemplate db;
    @Autowired PlatformTransactionManager transactions;

    /** Runs each task on its own thread, all released at once, and returns their results. */
    <T> List<T> together(List<Supplier<T>> tasks) throws Exception {
        var pool = Executors.newFixedThreadPool(tasks.size());
        var start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (var task : tasks) futures.add(pool.submit(() -> { start.await(); return task.get(); }));
            start.countDown();
            List<T> out = new ArrayList<>();
            for (var f : futures) out.add(f.get(60, TimeUnit.SECONDS));
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test void numbersIssuedAtOnceAreAllDifferentAndLeaveNoGaps() throws Exception {
        String issuer = "TEST:" + UUID.randomUUID().toString().substring(0, 8);
        var tx = new TransactionTemplate(transactions);
        try {
            // Eight at once on a brand-new series, each holding its transaction open a moment.
            List<Supplier<Long>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                tasks.add(() -> tx.execute(s -> {
                    long n = numbering.next(issuer, 'I', "T", "2026-27").sequence();
                    try { Thread.sleep(25); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    return n;
                }));
            }
            List<Long> got = together(tasks);
            assertEquals(LongStream.rangeClosed(1, 8).boxed().toList(), got.stream().sorted().toList(), "no two share a number: " + got);

            // An issue that fails gives its number back.
            assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> {
                assertEquals(9, numbering.next(issuer, 'I', "T", "2026-27").sequence());
                throw new IllegalStateException("insert failed");
            }));
            assertEquals("T/2627/000009", tx.execute(s -> numbering.next(issuer, 'I', "T", "2026-27")).documentNumber());
            // Each series and year counts on its own.
            assertEquals(1, tx.execute(s -> numbering.next(issuer, 'C', "C", "2026-27")).sequence());
            assertEquals(1, tx.execute(s -> numbering.next(issuer, 'I', "T", "2027-28")).sequence());
            assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                    () -> numbering.next(issuer, 'I', "T", "2026-27"), "numbers are only handed out inside the issuing transaction");
        } finally {
            db.update("DELETE FROM gst_document_sequence WHERE issuer=?", issuer);
        }
    }

    @Test void twoInvoicesAtOnceNeverShareANumberAndOneBookingIsInvoicedOnce() throws Exception {
        var tx = new TransactionTemplate(transactions);
        long spa = db.queryForObject("SELECT MIN(s.id) FROM spa s JOIN spa_manager m ON m.id=s.spa_manager_id WHERE EXISTS (SELECT 1 FROM booking b WHERE b.spa_id=s.id)", Long.class);
        var before = db.queryForMap("SELECT gstin,legal_name FROM spa WHERE id=?", spa);
        List<Long> bookings = new ArrayList<>(), payments = new ArrayList<>();
        try {
            tx.executeWithoutResult(s -> {
                db.update("UPDATE spa SET gstin=? WHERE id=?", GstInvoiceTest.SPA_GSTIN, spa);
                long template = db.queryForObject("SELECT MIN(id) FROM booking WHERE spa_id=?", Long.class, spa);
                long paymentTemplate = db.queryForObject("SELECT MIN(id) FROM payment", Long.class);
                for (int i = 0; i < 4; i++) {
                    long b = GstRows.copy(db, "booking", template, Map.of("price_cents", 100000, "discount_cents", 0, "tax_cents", 18000,
                            "payment_type", "STANDARD", "payment_mode", "ONLINE", "status", "CONFIRMED", "guest_count", 1, "currency", "INR",
                            "booking_reference", GstRows.reference()));
                    var changes = new HashMap<String, Object>(Map.of("booking_id", b, "payment_status", "CAPTURED", "amount_cents", 118000,
                            "refunded_cents", 0, "captured_at", LocalDateTime.now(ZoneOffset.UTC), "currency", "INR"));
                    changes.put("refunded_at", null);
                    bookings.add(b);
                    payments.add(GstRows.copy(db, "payment", paymentTemplate, changes));
                }
            });

            // Four bookings of one spa invoiced at the same moment.
            List<Supplier<GstDocument>> tasks = new ArrayList<>();
            for (long b : bookings) tasks.add(() -> gst.issueForBooking(b).orElseThrow());
            List<GstDocument> docs = together(tasks);
            Set<String> numbers = new HashSet<>();
            docs.forEach(d -> numbers.add(d.documentNumber()));
            assertEquals(4, numbers.size(), "every invoice has its own number: " + numbers);
            long first = docs.stream().mapToLong(GstDocument::sequenceNumber).min().orElseThrow();
            assertEquals(LongStream.range(first, first + 4).boxed().toList(),
                    docs.stream().map(GstDocument::sequenceNumber).sorted().toList(), "consecutive, no gaps");

            // The same booking asked for twice at once still has one invoice.
            long b = bookings.get(0);
            List<GstDocument> twice = together(List.of(() -> gst.issueForBooking(b).orElseThrow(), () -> gst.issueForBooking(b).orElseThrow()));
            assertEquals(twice.get(0).id(), twice.get(1).id());
            assertEquals(docs.get(0).id(), twice.get(0).id());
        } finally {
            tx.executeWithoutResult(s -> {
                for (long b : bookings) db.update("DELETE FROM gst_document WHERE booking_id=?", b);
                for (long p : payments) db.update("DELETE FROM payment WHERE id=?", p);
                for (long b : bookings) db.update("DELETE FROM booking WHERE id=?", b);
                db.update("UPDATE spa SET gstin=?,legal_name=? WHERE id=?", before.get("gstin"), before.get("legal_name"), spa);
            });
        }
    }
}
