package com.nirvana.application.service.payout;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Settles what Nirvana owes spas. A run gathers every online booking payment, package sale and online refund
 * up to a cut-off that is not yet in a payout, and writes one payout per spa and currency: money in, less
 * refunds, less commission ({@link PayoutMath}). Each item is recorded in payout_item under a unique key, so
 * re-running, or two runs at once, can't put the same money in two payouts. Desk money (spa_desk_receipt) is
 * the spa's already and never appears here.
 * <p>
 * A spa whose refunds exceed its takings gets no payout: its items wait and are netted against the next run.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutService {
    public static final List<String> STATUSES = List.of("PENDING", "IN_PROGRESS", "COMPLETED", "FAILED");
    private static final String PAID = "('CAPTURED','COMPLETED','REFUNDED','PARTIALLY_REFUNDED')";
    /** Online money not yet in a payout. Refunds count only when the money they return was itself paid out. */
    private static final String UNSETTLED = """
        SELECT * FROM (
          SELECT 'BOOKING' AS kind,p.id AS sourceId,0 AS seq,b.spa_id AS spa,p.currency AS currency,p.amount_cents AS amount,
                 p.captured_at AS at,b.booking_reference AS reference
          FROM payment p JOIN booking b ON b.id=p.booking_id
          WHERE p.captured_at<:cutoff AND p.captured_at>=:since AND p.payment_status IN %1$s
          UNION ALL
          SELECT 'BOOKING_REFUND',f.payment_id,f.cumulative_cents,b.spa_id,p.currency,-f.amount_cents,f.recorded_at,b.booking_reference
          FROM booking_refund_posting f JOIN payment p ON p.id=f.payment_id JOIN booking b ON b.id=p.booking_id
          WHERE f.recorded_at<:cutoff AND p.captured_at>=:since AND p.payment_status IN %1$s
          UNION ALL
          SELECT 'PACKAGE',x.id,0,x.spa_id,COALESCE(x.currency,'INR'),ROUND(x.price_paid*100),COALESCE(x.collected_at,x.purchase_date),CONCAT('Package ',x.id)
          FROM user_spa_package_subscription x
          WHERE x.payment_status='SUCCESS' AND COALESCE(x.collected_at,x.purchase_date)<:cutoff AND COALESCE(x.collected_at,x.purchase_date)>=:since
          UNION ALL
          SELECT 'PACKAGE_REFUND',f.subscription_id,f.cumulative_cents,x.spa_id,COALESCE(x.currency,'INR'),-f.amount_cents,f.recorded_at,CONCAT('Package ',x.id)
          FROM spa_package_refund_posting f JOIN user_spa_package_subscription x ON x.id=f.subscription_id
          WHERE f.recorded_at<:cutoff AND COALESCE(x.collected_at,x.purchase_date)>=:since
        ) e WHERE e.spa IS NOT NULL AND e.amount<>0 AND NOT EXISTS
          (SELECT 1 FROM payout_item i WHERE i.kind=e.kind AND i.source_id=e.sourceId AND i.source_seq=e.seq)
        ORDER BY e.spa,e.currency,e.at
        """.formatted(PAID);
    public static final String COLUMNS = """
        p.id,p.spa_id AS spaId,s.name AS spaName,p.payout_reference AS reference,p.currency,p.status,p.period_start AS periodStart,
        p.period_end AS periodEnd,p.gross_cents AS gross,p.refunds_cents AS refunds,p.commission_pct AS commissionPct,
        p.commission_cents AS commission,p.amount_cents AS net,p.provider,p.transfer_id AS transferId,p.failure_reason AS failureReason,
        p.attempts,p.last_attempt_at AS lastAttemptAt,p.paid_at AS paidAt,p.bank_account_masked AS bankAccount,p.created_at AS createdAt
        """;

    private final NamedParameterJdbcTemplate db;
    private final TransactionTemplate tx;
    private final PayoutProvider provider;
    private final PayoutProperties props;
    private final ReentrantLock running = new ReentrantLock();
    private final SecureRandom random = new SecureRandom();

    public record RunResult(LocalDateTime cutoff, int created, int carriedForward, int sent, int completed, int failed) {}

    /** The run's cut-off: midnight (in payouts.zone) at the start of today less the hold, as UTC. Later money waits. */
    public LocalDateTime defaultCutoff() {
        return midnight(LocalDate.now(props.getZone()).minusDays(Math.max(0, props.getHoldDays())));
    }

    /** The start of {@code day} in payouts.zone, as the UTC time the database holds. */
    public LocalDateTime midnight(LocalDate day) {
        return day.atStartOfDay(props.getZone()).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    public RunResult run() { return run(defaultCutoff()); }

    public RunResult run(LocalDateTime cutoff) {
        if (cutoff.isAfter(now())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The cut-off can't be in the future");
        if (!running.tryLock()) throw new ResponseStatusException(HttpStatus.CONFLICT, "A settlement run is already in progress");
        try {
            var since = props.getSince() == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : props.getSince().atStartOfDay();
            var rows = db.queryForList(UNSETTLED, Map.of("cutoff", cutoff, "since", since));
            Map<List<Object>, List<Map<String, Object>>> groups = rows.stream().collect(Collectors.groupingBy(
                    r -> List.of(r.get("spa"), r.get("currency")), LinkedHashMap::new, Collectors.toList()));
            int created = 0, carried = 0;
            for (var group : groups.values()) {
                try {
                    Long id = tx.execute(s -> settle(group, cutoff));
                    if (id == null) carried++; else created++;
                } catch (DuplicateKeyException e) {
                    // Another run took these items first: they're settled, just not by us.
                    log.info("event=payout.items_taken spa={}", group.get(0).get("spa"));
                }
            }
            int[] sent = sendDue();
            log.info("event=payout.run cutoff={} created={} carried={} sent={} completed={} failed={}", cutoff, created, carried, sent[0], sent[1], sent[2]);
            return new RunResult(cutoff, created, carried, sent[0], sent[1], sent[2]);
        } finally {
            running.unlock();
        }
    }

    /** Writes one payout for a spa's unsettled items; null when what's owed is negative (carried forward). */
    private Long settle(List<Map<String, Object>> items, LocalDateTime cutoff) {
        long spa = num(items.get(0).get("spa"));
        String currency = String.valueOf(items.get(0).get("currency"));
        long gross = 0, refunds = 0;
        for (var i : items) {
            long amount = num(i.get("amount"));
            if (amount > 0) gross += amount; else refunds -= amount;
        }
        Integer pct = db.queryForObject("SELECT commission_pct FROM spa WHERE id=:spa", Map.of("spa", spa), Integer.class);
        var split = PayoutMath.split(gross, refunds, pct == null ? 0 : pct);
        if (split.net() < 0) return null;
        if (split.net() > Integer.MAX_VALUE) throw new IllegalStateException("Payout too large for spa " + spa);
        var args = new MapSqlParameterSource(Map.of("spa", spa, "currency", currency, "cutoff", cutoff));
        LocalDateTime previous = db.queryForObject("SELECT MAX(period_end) FROM payout WHERE spa_id=:spa AND currency=:currency",
                args, LocalDateTime.class);
        LocalDateTime first = time(items.get(0).get("at"));
        boolean nothing = split.net() == 0;
        args.addValue("start", previous != null && previous.isBefore(cutoff) ? previous : first)
            .addValue("reference", reference(spa, cutoff)).addValue("gross", gross).addValue("refunds", refunds)
            .addValue("pct", split.commissionPct()).addValue("commission", split.commission()).addValue("net", (int) split.net())
            .addValue("status", nothing ? "COMPLETED" : "PENDING").addValue("now", now())
            .addValue("reason", nothing ? "Nothing to transfer" : null)
            .addValue("ids", items.stream().filter(i -> "BOOKING".equals(i.get("kind"))).map(i -> String.valueOf(i.get("sourceId")))
                    .collect(Collectors.joining(",", "[", "]")));
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        db.update("""
            INSERT INTO payout(created_at,updated_at,spa_id,payout_reference,currency,status,period_start,period_end,gross_cents,
              refunds_cents,commission_pct,commission_cents,amount_cents,fees_cents,payment_ids,failure_reason,attempts,paid_at,version)
            VALUES(:now,:now,:spa,:reference,:currency,:status,:start,:cutoff,:gross,:refunds,:pct,:commission,:net,0,:ids,:reason,0,
              CASE WHEN :status='COMPLETED' THEN :now END,0)
            """, args, key, new String[]{"id"});
        long id = key.getKey().longValue();
        db.batchUpdate("INSERT INTO payout_item(payout_id,kind,source_id,source_seq,amount_cents,occurred_at,reference) VALUES(:payout,:kind,:sourceId,:seq,:amount,:at,:reference)",
                items.stream().map(i -> { var m = new HashMap<>(i); m.put("payout", id); return new MapSqlParameterSource(m); })
                        .toArray(MapSqlParameterSource[]::new));
        return id;
    }

    private String reference(long spa, LocalDateTime cutoff) {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        var sb = new StringBuilder("PO-").append(spa).append('-').append(cutoff.format(DateTimeFormatter.ofPattern("yyMMdd"))).append('-');
        for (int i = 0; i < 5; i++) sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return sb.toString();
    }

    /** Between runs: sends payouts that were waiting (e.g. for bank details) and checks transfers in flight. */
    public void sendWaiting() {
        if (!provider.automatic() || !running.tryLock()) return;
        try { sendDue(); } finally { running.unlock(); }
    }

    /** Sends every payout that is waiting (or checks those in flight). Returns {sent, completed, failed}. */
    private int[] sendDue() {
        int sent = 0, completed = 0, failed = 0;
        for (Long id : db.queryForList("SELECT id FROM payout WHERE status IN ('PENDING','IN_PROGRESS') AND period_end IS NOT NULL ORDER BY id",
                Map.of(), Long.class)) {
            String status = send(id);
            if (status == null) continue;
            sent++;
            if ("COMPLETED".equals(status)) completed++;
            if ("FAILED".equals(status)) failed++;
        }
        return new int[]{sent, completed, failed};
    }

    /**
     * Pays one payout: claims it (so two callers can't both transfer), then asks the provider. Returns the new
     * status, or null when nothing was attempted (manual mode, no bank account yet, or another caller has it).
     */
    public String send(long id) {
        var p = payout(id);
        String status = (String) p.get("status");
        if ("COMPLETED".equals(status)) return null;
        if ("IN_PROGRESS".equals(status) && p.get("transferId") != null) return poll(id, (String) p.get("transferId"));
        var account = db.queryForList("SELECT * FROM spa_payout_account WHERE spa_id=:spa", Map.of("spa", p.get("spaId")));
        String waiting = account.isEmpty() ? "Waiting for the spa's bank details"
                : !provider.automatic() ? "Waiting for a manual bank transfer"
                : account.get(0).get("route_account_id") == null ? "Waiting for the spa's Razorpay linked account" : null;
        if (waiting != null) {
            db.update("UPDATE payout SET failure_reason=:why,updated_at=:now WHERE id=:id AND status IN ('PENDING','FAILED')",
                    Map.of("why", waiting, "now", now(), "id", id));
            return null;
        }
        var a = account.get(0);
        String masked = "XXXX" + a.get("account_last4") + " · " + a.get("ifsc");
        // A stale claim with no transfer id (crash or no answer mid-transfer) can be taken again; it keeps its
        // attempt number, so the provider sees the same idempotency key and can't pay twice.
        int claimed = db.update("""
            UPDATE payout SET attempts=attempts+CASE WHEN status='IN_PROGRESS' THEN 0 ELSE 1 END,status='IN_PROGRESS',
              last_attempt_at=:now,updated_at=:now,provider=:provider,bank_account_masked=:bank,failure_reason=NULL
            WHERE id=:id AND amount_cents>0 AND (status IN ('PENDING','FAILED')
              OR (status='IN_PROGRESS' AND transfer_id IS NULL AND (last_attempt_at IS NULL OR last_attempt_at<:stale)))
            """, Map.of("now", now(), "stale", now().minusHours(1), "provider", provider.name(), "bank", masked, "id", id));
        if (claimed != 1) return null;
        p = payout(id);
        var result = provider.transfer(new PayoutProvider.TransferRequest(id, (String) p.get("reference"),
                p.get("reference") + "-a" + p.get("attempts"), num(p.get("net")), (String) p.get("currency"),
                (String) a.get("route_account_id"), num(p.get("spaId"))));
        return record(id, result);
    }

    private String poll(long id, String transferId) {
        return record(id, provider.status(transferId));
    }

    private String record(long id, PayoutProvider.Transfer t) {
        String status = t.state().name();
        db.update("""
            UPDATE payout SET status=:status,transfer_id=COALESCE(:transfer,transfer_id),failure_reason=:why,updated_at=:now,
              paid_at=CASE WHEN :status='COMPLETED' THEN :now ELSE paid_at END
            WHERE id=:id AND status='IN_PROGRESS'
            """, new MapSqlParameterSource().addValue("status", status).addValue("transfer", t.transferId())
                .addValue("why", t.message()).addValue("now", now()).addValue("id", id));
        if (!"COMPLETED".equals(status)) log.warn("event=payout.transfer payout={} status={}", id, status);
        return status;
    }

    /** Admin: try a failed or waiting payout again. */
    public Map<String, Object> retry(long id) {
        var p = payout(id);
        if ("COMPLETED".equals(p.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "This payout is already paid");
        send(id);
        return payout(id);
    }

    /** Admin: the spa was paid outside the gateway (manual mode, or checked on the Razorpay dashboard). */
    public Map<String, Object> markPaid(long id, String reference) {
        String ref = reference == null ? "" : reference.trim();
        if (ref.length() < 4 || ref.length() > 64) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the bank or Razorpay reference (4 to 64 characters)");
        var p = payout(id);
        if ("IN_PROGRESS".equals(p.get("status")) && p.get("transferId") != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A Razorpay transfer is in flight for this payout; wait for it to finish");
        try {
            int n = db.update("""
                UPDATE payout SET status='COMPLETED',transfer_id=:ref,provider=COALESCE(provider,'MANUAL'),failure_reason=NULL,paid_at=:now,updated_at=:now
                WHERE id=:id AND status<>'COMPLETED'
                """, Map.of("ref", ref, "now", now(), "id", id));
            if (n != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "This payout is already paid");
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That reference is already recorded on another payout");
        }
        return payout(id);
    }

    public Map<String, Object> payout(long id) {
        var rows = db.queryForList("SELECT " + COLUMNS + " FROM payout p JOIN spa s ON s.id=p.spa_id WHERE p.id=:id", Map.of("id", id));
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such payout");
        return rows.get(0);
    }

    /** One payout with the payments and refunds it covers. */
    public Map<String, Object> statement(long id) {
        var result = new LinkedHashMap<>(payout(id));
        result.put("items", db.queryForList("""
            SELECT kind,reference,amount_cents AS amount,occurred_at AS occurredAt FROM payout_item WHERE payout_id=:id ORDER BY occurred_at,id
            """, Map.of("id", id)));
        return result;
    }

    public List<Map<String, Object>> list(Long spa, String status, int limit, int offset) {
        if (status != null && !STATUSES.contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status");
        var args = new MapSqlParameterSource().addValue("spa", spa).addValue("status", status).addValue("limit", limit).addValue("offset", offset);
        return db.queryForList("SELECT " + COLUMNS + " FROM payout p JOIN spa s ON s.id=p.spa_id WHERE (:spa IS NULL OR p.spa_id=:spa)"
                + " AND (:status IS NULL OR p.status=:status) ORDER BY p.id DESC LIMIT :limit OFFSET :offset", args);
    }

    /** Online money for this spa not yet in a payout, as the next statement would show it today. */
    public List<Map<String, Object>> upcoming(long spa) {
        var since = props.getSince() == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : props.getSince().atStartOfDay();
        var rows = db.queryForList(UNSETTLED.replace("WHERE e.spa IS NOT NULL", "WHERE e.spa=:spa"),
                Map.of("spa", spa, "cutoff", now(), "since", since));
        Integer pct = db.queryForObject("SELECT commission_pct FROM spa WHERE id=:spa", Map.of("spa", spa), Integer.class);
        var byCurrency = new TreeMap<String, long[]>();
        for (var r : rows) {
            long amount = num(r.get("amount"));
            var t = byCurrency.computeIfAbsent(String.valueOf(r.get("currency")), c -> new long[3]);
            if (amount > 0) t[0] += amount; else t[1] -= amount;
            t[2]++;
        }
        var out = new ArrayList<Map<String, Object>>();
        byCurrency.forEach((currency, t) -> {
            var split = PayoutMath.split(t[0], t[1], pct == null ? 0 : pct);
            var m = new LinkedHashMap<String, Object>();
            m.put("currency", currency); m.put("gross", split.gross()); m.put("refunds", split.refunds());
            m.put("commissionPct", split.commissionPct()); m.put("commission", split.commission()); m.put("net", split.net());
            m.put("items", t[2]);
            out.add(m);
        });
        return out;
    }

    public String mode() { return provider.name(); }
    public boolean automatic() { return provider.automatic(); }

    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    /** The driver may return DATETIME as LocalDateTime or Timestamp. */
    static LocalDateTime time(Object v) {
        if (v instanceof LocalDateTime t) return t;
        if (v instanceof java.sql.Timestamp t) return t.toLocalDateTime();
        if (v instanceof java.time.OffsetDateTime t) return t.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        return LocalDateTime.parse(String.valueOf(v).replace(' ', 'T'));
    }
    private static long num(Object v) { return v == null ? 0 : ((Number) v).longValue(); }
}
