package com.nirvana.application.service.gst;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * Catches up on GST documents nothing asked for yet: invoices for paid bookings and packages,
 * credit notes for refunds, and last month's commission invoices. Every issue is idempotent, so
 * overlapping runs (or a request issuing the same document) are harmless.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GstInvoiceJob {
    private final GstInvoiceService gst;
    private final JdbcTemplate db;

    @Value("${gst.sweep.lookback-days:45}") int lookbackDays;
    @Value("${gst.nirvana.gstin:}") String nirvanaGstin;

    @Scheduled(initialDelayString = "${gst.sweep.initial-delay-ms:300000}", fixedDelayString = "${gst.sweep.interval-ms:600000}")
    public void sweep() {
        if (lookbackDays <= 0) return;
        // Paid online, paid at the desk, or a completed pay-at-spa booking, with no invoice yet.
        each("booking invoice", db.queryForList("""
                SELECT b.id FROM booking b WHERE b.payment_type<>'PAID_BY_PACKAGE' AND b.updated_at>=UTC_TIMESTAMP()-INTERVAL ? DAY
                  AND NOT EXISTS (SELECT 1 FROM gst_document g WHERE g.source_key=CONCAT('BOOKING:',b.id))
                  AND (EXISTS (SELECT 1 FROM payment p WHERE p.booking_id=b.id AND p.payment_status IN ('CAPTURED','COMPLETED','PARTIALLY_REFUNDED','REFUNDED'))
                    OR (b.payment_mode='OFFLINE' AND (b.status='COMPLETED' OR EXISTS (SELECT 1 FROM spa_desk_receipt d WHERE d.booking_id=b.id))))
                ORDER BY b.id LIMIT 500""", Long.class, lookbackDays), gst::issueForBooking);
        // Refunds with no credit note yet.
        each("booking credit note", db.queryForList("""
                SELECT DISTINCT p.booking_id FROM booking_refund_posting f JOIN payment p ON p.id=f.payment_id
                WHERE f.recorded_at>=UTC_TIMESTAMP()-INTERVAL ? DAY
                  AND NOT EXISTS (SELECT 1 FROM gst_document g WHERE g.source_key=CONCAT('REFUND:BOOKING:',f.payment_id,':',f.cumulative_cents))
                UNION
                SELECT DISTINCT d.booking_id FROM spa_desk_receipt d WHERE d.returned_at>=UTC_TIMESTAMP()-INTERVAL ? DAY
                  AND NOT EXISTS (SELECT 1 FROM gst_document g WHERE g.source_key=CONCAT('REFUND:DESK:',d.id))
                LIMIT 500""", Long.class, lookbackDays, lookbackDays), gst::creditBookingRefunds);
        each("package invoice", db.queryForList("""
                SELECT x.id FROM user_spa_package_subscription x WHERE x.payment_status='SUCCESS'
                  AND COALESCE(x.collected_at,x.purchase_date)>=UTC_TIMESTAMP()-INTERVAL ? DAY
                  AND NOT EXISTS (SELECT 1 FROM gst_document g WHERE g.source_key=CONCAT('PACKAGE:',x.id))
                ORDER BY x.id LIMIT 500""", Long.class, lookbackDays), gst::issueForPackage);
        each("package credit note", db.queryForList("""
                SELECT DISTINCT f.subscription_id FROM spa_package_refund_posting f WHERE f.recorded_at>=UTC_TIMESTAMP()-INTERVAL ? DAY
                  AND NOT EXISTS (SELECT 1 FROM gst_document g WHERE g.source_key=CONCAT('REFUND:PACKAGE:',f.subscription_id,':',f.cumulative_cents))
                LIMIT 500""", Long.class, lookbackDays), gst::creditPackageRefunds);
        commissions();
    }

    /** Last month's commission invoices, for spas that had online collections then. */
    void commissions() {
        if (GstMath.normalizeGstin(nirvanaGstin) == null) return;
        YearMonth last = YearMonth.now(ZoneId.of("Asia/Kolkata")).minusMonths(1);
        LocalDate from = last.atDay(1).minusDays(1), to = last.atEndOfMonth().plusDays(1);
        each("commission invoice", db.queryForList("""
                SELECT s.id FROM spa s WHERE s.commission_pct>0
                  AND NOT EXISTS (SELECT 1 FROM gst_document g WHERE g.source_key=CONCAT('COMMISSION:',s.id,':',?))
                  AND (EXISTS (SELECT 1 FROM booking b JOIN payment p ON p.booking_id=b.id WHERE b.spa_id=s.id AND p.captured_at BETWEEN ? AND ?)
                    OR EXISTS (SELECT 1 FROM user_spa_package_subscription x WHERE x.spa_id=s.id AND x.payment_status='SUCCESS'
                               AND COALESCE(x.collected_at,x.purchase_date) BETWEEN ? AND ?))
                ORDER BY s.id LIMIT 500""", Long.class, last.toString(), from, to, from, to), id -> gst.issueCommission(id, last));
    }

    /** Each in its own transaction (through the service's proxy), so one failure doesn't stop the rest. */
    private void each(String what, List<Long> ids, LongConsumer issue) {
        for (Long id : ids) {
            try {
                issue.accept(id);
            } catch (RuntimeException e) {
                log.warn("event=gst.sweep.failed what={} id={} error={}", what, id, e.toString());
            }
        }
    }
}
