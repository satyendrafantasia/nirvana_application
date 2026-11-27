package com.nirvana.application.service.gst;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Issues GST documents under the marketplace model.
 *
 * <ul>
 *   <li>The spa is the supplier to the guest. A spa with a GSTIN issues a tax invoice for each paid
 *       booking and each package purchase; one without issues a bill of supply, with no GST.</li>
 *   <li>Each refund (online, wallet credit or a desk return) gets a credit note against that invoice.</li>
 *   <li>Nirvana invoices the spa once a month for its commission on online collections, plus GST.</li>
 * </ul>
 *
 * Every document is issued once (its source_key is unique), numbered from a gap-free series per
 * issuer and financial year (see {@link GstNumbering}), and frozen at issue.
 */
@Service
@Slf4j
public class GstInvoiceService {
    static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final List<Integer> STANDARD_RATES = List.of(0, 500, 1200, 1800, 2800, 4000);

    private final JdbcTemplate db;
    private final NamedParameterJdbcTemplate named;
    private final GstNumbering numbering;
    private final GstMail mail;
    private final SimpleJdbcInsert insert;
    private final Clock clock;
    private final jakarta.persistence.EntityManager em;

    @Value("${gst.default-sac:999722}") String defaultSac;
    @Value("${gst.default-rate-pct:18}") BigDecimal defaultRatePct;
    @Value("${gst.nirvana.legal-name:Nirvana Wellness Private Limited}") String nirvanaName;
    @Value("${gst.nirvana.gstin:}") String nirvanaGstin;
    @Value("${gst.nirvana.address:}") String nirvanaAddress;
    @Value("${gst.commission.sac:998599}") String commissionSac;
    @Value("${gst.commission.rate-pct:18}") BigDecimal commissionRatePct;

    public GstInvoiceService(JdbcTemplate db, NamedParameterJdbcTemplate named, GstNumbering numbering, GstMail mail,
                             Optional<Clock> clock, jakarta.persistence.EntityManager em) {
        this.em = em;
        this.db = db;
        this.named = named;
        this.numbering = numbering;
        this.mail = mail;
        this.clock = clock.orElse(Clock.systemUTC());
        this.insert = new SimpleJdbcInsert(db).withTableName("gst_document").usingGeneratedKeyColumns("id");
    }

    // ------------------------------------------------------------------ reading

    private static final DataClassRowMapper<GstDocument> ROW = new DataClassRowMapper<>(GstDocument.class);

    public Optional<GstDocument> find(long id) {
        return db.query("SELECT * FROM gst_document WHERE id=?", ROW, id).stream().findFirst();
    }

    public Optional<GstDocument> bySource(String sourceKey) {
        return db.query("SELECT * FROM gst_document WHERE source_key=?", ROW, sourceKey).stream().findFirst();
    }

    public List<GstDocument> forBooking(long bookingId) {
        return db.query("SELECT * FROM gst_document WHERE booking_id=? ORDER BY issued_at,id", ROW, bookingId);
    }

    public List<GstDocument> forSubscription(long subscriptionId) {
        return db.query("SELECT * FROM gst_document WHERE subscription_id=? ORDER BY issued_at,id", ROW, subscriptionId);
    }

    /** A spa's documents issued between two dates (inclusive, India time), newest first. */
    public List<GstDocument> forSpa(long spaId, LocalDate from, LocalDate to) {
        return db.query("SELECT * FROM gst_document WHERE spa_id=? AND issue_date BETWEEN ? AND ? ORDER BY issued_at DESC,id DESC",
                ROW, spaId, from, to);
    }

    // ------------------------------------------------------------------ guest invoices

    /**
     * The booking's invoice (or bill of supply), issued now if it has not been. Empty when the booking
     * has nothing to invoice yet: unpaid, or paid with package sessions (the package was invoiced).
     */
    @Transactional
    public Optional<GstDocument> issueForBooking(long bookingId) {
        flush();
        // Lock the booking so two requests for the same invoice queue and the second finds the first's.
        var rows = db.queryForList("""
                SELECT b.id,b.spa_id,b.user_id,b.price_cents,b.discount_cents,b.tax_cents,b.currency,b.guest_count,
                  b.payment_type,b.payment_mode,b.status,b.booking_reference,b.start_ts,b.remainder_cents,
                  s.name AS service_name,s.sac_code,s.gst_rate_pct
                FROM booking b JOIN service s ON s.id=b.service_id WHERE b.id=? FOR UPDATE""", bookingId);
        if (rows.isEmpty()) throw new jakarta.persistence.EntityNotFoundException("Booking not found: " + bookingId);
        var b = rows.get(0);
        String key = "BOOKING:" + bookingId;
        var existing = bySource(key);
        if (existing.isPresent()) return existing;
        if (!invoiceable(b)) return Optional.empty();

        long spaId = num(b.get("spa_id"));
        Supplier spa = spa(spaId);
        long price = num(b.get("price_cents")), discount = num(b.get("discount_cents")), tax = num(b.get("tax_cents"));
        int quantity = (int) Math.max(1, num(b.get("guest_count")));
        String serviceName = str(b.get("service_name"));
        String description = serviceName + (quantity > 1 ? " (" + quantity + " guests)" : "");
        Recipient guest = guest(spaId, num(b.get("user_id")));

        Draft d = new Draft();
        d.sourceKey = key;
        d.spaId = spaId;
        d.userId = num(b.get("user_id"));
        d.bookingId = bookingId;
        d.description = description;
        d.sac = blank(str(b.get("sac_code"))) ? defaultSac : str(b.get("sac_code"));
        d.quantity = quantity;
        d.currency = blank(str(b.get("currency"))) ? "INR" : str(b.get("currency"));
        d.notes = "Booking " + b.get("booking_reference");
        if (spa.gstin == null) {
            // Not registered for GST, so no tax on the document. Anything an older booking charged as
            // tax is part of the price the guest paid.
            d.gross = price + tax;
            d.discount = discount;
            d.taxable = price + tax - discount;
            d.rateBps = 0;
            d.split = new GstMath.Split(0, 0, 0);
        } else {
            d.gross = price;
            d.discount = discount;
            d.taxable = price - discount;
            int configured = ratePct(b.get("gst_rate_pct"), spaId);
            d.rateBps = rateCharged(d.taxable, tax, configured);
            // The tax is what the guest was charged, so the invoice adds up to the payment.
            d.split = GstMath.split(tax, true);
        }
        return Optional.of(issueSpaDocument(spa, guest, d));
    }

    /** Paid online, paid at the desk, or completed (pay-at-spa bookings are settled by then). */
    private boolean invoiceable(Map<String, Object> b) {
        if ("PAID_BY_PACKAGE".equals(b.get("payment_type"))) return false;
        long id = num(b.get("id"));
        Integer paid = db.queryForObject("""
                SELECT COUNT(*) FROM payment WHERE booking_id=? AND payment_status IN ('CAPTURED','COMPLETED','PARTIALLY_REFUNDED','REFUNDED')""",
                Integer.class, id);
        if (paid != null && paid > 0) return true;
        if (num(b.get("remainder_cents")) == 0) {
            Integer walletPaid = db.queryForObject("SELECT COUNT(*) FROM booking_wallet_spend WHERE booking_id=? AND amount_cents>0", Integer.class, id);
            if (walletPaid != null && walletPaid > 0) return true;
        }
        if (!"OFFLINE".equals(b.get("payment_mode"))) return false;
        Integer desk = db.queryForObject("SELECT COUNT(*) FROM spa_desk_receipt WHERE booking_id=?", Integer.class, id);
        return (desk != null && desk > 0) || "COMPLETED".equals(b.get("status"));
    }

    /** The package purchase's invoice. Package prices include GST, so the tax is taken out of the price. */
    @Transactional
    public Optional<GstDocument> issueForPackage(long subscriptionId) {
        flush();
        var rows = db.queryForList("""
                SELECT x.id,x.spa_id,x.user_id,x.price_paid,x.currency,x.payment_status,x.level,x.total_sessions
                FROM user_spa_package_subscription x WHERE x.id=? FOR UPDATE""", subscriptionId);
        if (rows.isEmpty()) throw new jakarta.persistence.EntityNotFoundException("Package purchase not found: " + subscriptionId);
        var x = rows.get(0);
        String key = "PACKAGE:" + subscriptionId;
        var existing = bySource(key);
        if (existing.isPresent()) return existing;
        if (!"SUCCESS".equals(x.get("payment_status"))) return Optional.empty();

        long spaId = num(x.get("spa_id"));
        Supplier spa = spa(spaId);
        long gross = ((BigDecimal) x.get("price_paid")).movePointRight(2).longValue();
        Draft d = new Draft();
        d.sourceKey = key;
        d.spaId = spaId;
        d.userId = num(x.get("user_id"));
        d.subscriptionId = subscriptionId;
        String level = str(x.get("level"));
        d.description = "Spa package" + (blank(level) ? "" : " - " + level.charAt(0) + level.substring(1).toLowerCase(Locale.ROOT))
                + " (" + x.get("total_sessions") + " sessions)";
        d.sac = defaultSac;
        d.quantity = 1;
        d.currency = blank(str(x.get("currency"))) ? "INR" : str(x.get("currency"));
        d.gross = gross;
        d.discount = 0;
        if (spa.gstin == null) {
            d.taxable = gross;
            d.rateBps = 0;
            d.split = new GstMath.Split(0, 0, 0);
        } else {
            d.rateBps = GstMath.bps(BigDecimal.valueOf(ratePct(null, spaId)));
            d.taxable = GstMath.taxableFromInclusive(gross, d.rateBps);
            d.gross = d.taxable;
            d.split = GstMath.split(gross - d.taxable, true);
        }
        d.notes = "Package purchase " + subscriptionId;
        return Optional.of(issueSpaDocument(spa, guest(spaId, d.userId), d));
    }

    // ------------------------------------------------------------------ credit notes

    /** A refund that the original invoice must be credited for. */
    record Refund(String sourceKey, long amountCents, String what) {}

    /** Issues a credit note for each of the booking's refunds that has none yet. Returns the new ones. */
    @Transactional
    public List<GstDocument> creditBookingRefunds(long bookingId) {
        var invoice = issueForBooking(bookingId);
        if (invoice.isEmpty()) return List.of();
        List<Refund> refunds = new ArrayList<>();
        db.query("""
                SELECT f.payment_id,f.cumulative_cents,f.amount_cents FROM booking_refund_posting f JOIN payment p ON p.id=f.payment_id
                WHERE p.booking_id=? ORDER BY f.recorded_at,f.cumulative_cents""", rs -> {
            refunds.add(new Refund("REFUND:BOOKING:" + rs.getLong(1) + ":" + rs.getInt(2), rs.getLong(3), "Refund"));
        }, bookingId);
        db.query("SELECT returned_cents FROM booking_wallet_spend WHERE booking_id=? AND returned_cents>0",
                rs -> { refunds.add(new Refund("REFUND:WALLET:" + bookingId, rs.getLong(1), "Returned to Nirvana wallet")); }, bookingId);
        db.query("SELECT id,amount_cents FROM spa_desk_receipt WHERE booking_id=? AND returned_at IS NOT NULL ORDER BY returned_at,id",
                rs -> { refunds.add(new Refund("REFUND:DESK:" + rs.getLong(1), rs.getLong(2), "Amount returned at the spa")); }, bookingId);
        return credit(invoice.get(), refunds);
    }

    @Transactional
    public List<GstDocument> creditPackageRefunds(long subscriptionId) {
        var invoice = issueForPackage(subscriptionId);
        if (invoice.isEmpty()) return List.of();
        List<Refund> refunds = new ArrayList<>();
        db.query("SELECT cumulative_cents,amount_cents FROM spa_package_refund_posting WHERE subscription_id=? ORDER BY recorded_at,cumulative_cents",
                rs -> { refunds.add(new Refund("REFUND:PACKAGE:" + subscriptionId + ":" + rs.getInt(1), rs.getLong(2), "Package refund")); },
                subscriptionId);
        return credit(invoice.get(), refunds);
    }

    /**
     * Credits each refund against the invoice, in the invoice's proportions of taxable value and tax,
     * never crediting more than the invoice's total. The caller holds the booking or package lock.
     */
    private List<GstDocument> credit(GstDocument invoice, List<Refund> refunds) {
        List<GstDocument> issued = new ArrayList<>();
        long credited = Optional.ofNullable(db.queryForObject(
                "SELECT COALESCE(SUM(total_cents),0) FROM gst_document WHERE original_document_id=?", Long.class, invoice.id())).orElse(0L);
        for (Refund r : refunds) {
            if (bySource(r.sourceKey()).isPresent()) continue;
            long amount = Math.min(r.amountCents(), invoice.totalCents() - credited);
            if (amount <= 0) continue;
            Draft d = new Draft();
            d.sourceKey = r.sourceKey();
            d.spaId = invoice.spaId();
            d.userId = invoice.userId();
            d.bookingId = invoice.bookingId();
            d.subscriptionId = invoice.subscriptionId();
            d.originalId = invoice.id();
            d.description = r.what() + " against " + invoice.documentNumber() + ": " + invoice.description();
            d.sac = invoice.sacCode();
            d.quantity = 1;
            d.currency = invoice.currency();
            d.rateBps = invoice.rateBps();
            // The part of the refund that is tax, in the invoice's own proportion; the rest is taxable value.
            long tax = GstMath.proportion(invoice.taxCents(), amount, invoice.totalCents());
            d.taxable = amount - tax;
            d.gross = d.taxable;
            d.discount = 0;
            d.split = GstMath.split(tax, invoice.intraState());
            d.notes = "Credit note against " + invoice.title().toLowerCase(Locale.ROOT) + " " + invoice.documentNumber()
                    + " dated " + invoice.issueDate();
            GstDocument note = issueFrom(invoice, d);
            credited += note.totalCents();
            issued.add(note);
        }
        return issued;
    }

    // ------------------------------------------------------------------ Nirvana's commission invoice

    /**
     * Nirvana's invoice to the spa for its commission on the month's online collections (bookings and
     * packages paid through Nirvana, less refunds of them), at the spa's commission_pct, plus GST.
     * Empty when there is no commission for the month. Only for a month that has ended.
     */
    @Transactional
    public Optional<GstDocument> issueCommission(long spaId, YearMonth month) {
        flush();
        if (!month.isBefore(YearMonth.now(clock.withZone(INDIA)))) {
            throw new IllegalArgumentException("The commission invoice for " + month + " is issued once the month is over.");
        }
        String nirvana = GstMath.normalizeGstin(nirvanaGstin);
        if (nirvana == null) throw new IllegalStateException("Nirvana's GSTIN is not set (gst.nirvana.gstin).");
        var lock = db.queryForList("SELECT commission_pct FROM spa WHERE id=? FOR UPDATE", spaId);
        if (lock.isEmpty()) throw new jakarta.persistence.EntityNotFoundException("Spa not found: " + spaId);
        String key = "COMMISSION:" + spaId + ":" + month;
        var existing = bySource(key);
        if (existing.isPresent()) return existing;

        int pct = (int) num(lock.get(0).get("commission_pct"));
        long base = onlineNet(spaId, month);
        long commission = Math.round(Math.max(0, base) * pct / 100.0);
        if (commission <= 0) return Optional.empty();

        Supplier spa = spa(spaId);
        String nirvanaState = nirvana.substring(0, 2);
        // Business to business: the place of supply is where the spa is (IGST Act s.12(2)).
        String pos = spa.stateCode;
        Draft d = new Draft();
        d.sourceKey = key;
        d.spaId = spaId;
        d.description = "Marketplace commission on online bookings and packages, " + month.getMonth().getDisplayName(
                java.time.format.TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear() + " (" + pct + "% of "
                + rupees(base) + ")";
        d.sac = commissionSac;
        d.quantity = 1;
        d.currency = "INR";
        d.gross = commission;
        d.discount = 0;
        d.taxable = commission;
        d.rateBps = GstMath.bps(commissionRatePct);
        d.split = GstMath.split(GstMath.taxOnExclusive(commission, d.rateBps), nirvanaState.equals(pos));
        d.periodStart = month.atDay(1);
        d.periodEnd = month.atEndOfMonth();
        d.commissionBase = base;
        d.commissionPct = BigDecimal.valueOf(pct);
        d.notes = "Collections are counted on the day the money was received or refunded, India time.";

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.atOffset(ZoneOffset.UTC).atZoneSameInstant(INDIA).toLocalDate();
        String fy = GstMath.financialYear(today);
        var number = numbering.next("NIRVANA", 'I', "NV", fy);
        var row = new LinkedHashMap<String, Object>();
        row.put("doc_type", GstDocument.COMMISSION_INVOICE);
        row.put("issuer", "NIRVANA");
        row.put("supplier_name", nirvanaName);
        row.put("supplier_trade_name", "Nirvana");
        row.put("supplier_address", blank(nirvanaAddress) ? null : nirvanaAddress);
        row.put("supplier_gstin", nirvana);
        row.put("supplier_state", GstMath.stateName(nirvanaState));
        row.put("supplier_state_code", nirvanaState);
        row.put("recipient_name", spa.legalName);
        row.put("recipient_address", spa.address);
        row.put("recipient_gstin", spa.gstin);
        row.put("recipient_email", spa.email);
        row.put("recipient_phone", spa.phone);
        row.put("place_of_supply", pos == null ? spa.state : GstMath.stateName(pos));
        row.put("place_of_supply_code", pos);
        return Optional.of(save(row, d, number, now, today));
    }

    /** Online money in for the month, India time, less refunds of online money, in paise (INR only). */
    long onlineNet(long spaId, YearMonth month) {
        LocalDateTime start = month.atDay(1).atStartOfDay(INDIA).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay(INDIA).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        var p = new MapSqlParameterSource().addValue("spa", spaId).addValue("start", start).addValue("end", end);
        Long net = named.queryForObject("""
                SELECT COALESCE(SUM(collected),0)-COALESCE(SUM(refunded),0) FROM (
                  SELECT p.amount_cents AS collected,0 AS refunded FROM booking b JOIN payment p ON p.booking_id=b.id
                   WHERE b.spa_id=:spa AND p.captured_at>=:start AND p.captured_at<:end AND p.currency='INR'
                     AND p.payment_status IN ('CAPTURED','COMPLETED','REFUNDED','PARTIALLY_REFUNDED')
                  UNION ALL
                  SELECT 0,f.amount_cents FROM booking b JOIN payment p ON p.booking_id=b.id JOIN booking_refund_posting f ON f.payment_id=p.id
                   WHERE b.spa_id=:spa AND f.recorded_at>=:start AND f.recorded_at<:end AND p.currency='INR'
                  UNION ALL
                  SELECT w.amount_cents,0 FROM booking_wallet_spend w JOIN booking b ON b.id=w.booking_id
                   WHERE b.spa_id=:spa AND w.currency='INR' AND w.spent_at>=:start AND w.spent_at<:end
                  UNION ALL
                  SELECT 0,w.returned_cents FROM booking_wallet_spend w JOIN booking b ON b.id=w.booking_id
                   WHERE b.spa_id=:spa AND w.currency='INR' AND w.returned_at>=:start AND w.returned_at<:end
                  UNION ALL
                  SELECT ROUND(x.price_paid*100),0 FROM user_spa_package_subscription x
                   WHERE x.spa_id=:spa AND x.payment_status='SUCCESS' AND COALESCE(x.currency,'INR')='INR'
                     AND COALESCE(x.collected_at,x.purchase_date)>=:start AND COALESCE(x.collected_at,x.purchase_date)<:end
                  UNION ALL
                  SELECT 0,f.amount_cents FROM user_spa_package_subscription x JOIN spa_package_refund_posting f ON f.subscription_id=x.id
                   WHERE x.spa_id=:spa AND COALESCE(x.currency,'INR')='INR' AND f.recorded_at>=:start AND f.recorded_at<:end
                ) e""", p, Long.class);
        return net == null ? 0 : net;
    }

    // ------------------------------------------------------------------ issuing

    /**
     * Writes out pending JPA changes first: these reads are plain SQL, which Hibernate doesn't flush
     * for, and a caller such as payment capture has just marked the payment captured in its session.
     */
    private void flush() {
        if (em.isJoinedToTransaction()) em.flush();
    }

    /** What a document says, before it is numbered and saved. */
    static final class Draft {
        String sourceKey, description, sac, currency, notes;
        long spaId;
        Long userId, bookingId, subscriptionId, originalId;
        int quantity, rateBps;
        long gross, discount, taxable;
        GstMath.Split split;
        LocalDate periodStart, periodEnd;
        Long commissionBase;
        BigDecimal commissionPct;
    }

    private GstDocument issueSpaDocument(Supplier spa, Recipient guest, Draft d) {
        String type = spa.gstin == null ? GstDocument.BILL_OF_SUPPLY : GstDocument.TAX_INVOICE;
        char series = spa.gstin == null ? 'B' : 'I';
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.atOffset(ZoneOffset.UTC).atZoneSameInstant(INDIA).toLocalDate();
        var number = numbering.next(GstNumbering.spaIssuer(d.spaId), series, GstNumbering.spaPrefix(d.spaId, series),
                GstMath.financialYear(today));
        var row = spaRow(spa, guest, type);
        GstDocument doc = save(row, d, number, now, today);
        mail.afterCommit(doc, null);
        return doc;
    }

    private GstDocument issueFrom(GstDocument invoice, Draft d) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.atOffset(ZoneOffset.UTC).atZoneSameInstant(INDIA).toLocalDate();
        var number = numbering.next(invoice.issuer(), 'C', GstNumbering.spaPrefix(invoice.spaId(), 'C'), GstMath.financialYear(today));
        var row = new LinkedHashMap<String, Object>();
        row.put("doc_type", GstDocument.CREDIT_NOTE);
        row.put("issuer", invoice.issuer());
        // A credit note carries the parties as they were on the invoice.
        row.put("supplier_name", invoice.supplierName());
        row.put("supplier_trade_name", invoice.supplierTradeName());
        row.put("supplier_address", invoice.supplierAddress());
        row.put("supplier_gstin", invoice.supplierGstin());
        row.put("supplier_state", invoice.supplierState());
        row.put("supplier_state_code", invoice.supplierStateCode());
        row.put("recipient_name", invoice.recipientName());
        row.put("recipient_address", invoice.recipientAddress());
        row.put("recipient_gstin", invoice.recipientGstin());
        row.put("recipient_email", invoice.recipientEmail());
        row.put("recipient_phone", invoice.recipientPhone());
        row.put("place_of_supply", invoice.placeOfSupply());
        row.put("place_of_supply_code", invoice.placeOfSupplyCode());
        GstDocument doc = save(row, d, number, now, today);
        mail.afterCommit(doc, invoice);
        return doc;
    }

    private Map<String, Object> spaRow(Supplier spa, Recipient guest, String type) {
        var row = new LinkedHashMap<String, Object>();
        row.put("doc_type", type);
        row.put("issuer", GstNumbering.spaIssuer(spa.id));
        row.put("supplier_name", spa.legalName);
        row.put("supplier_trade_name", spa.legalName.equals(spa.name) ? null : spa.name);
        row.put("supplier_address", spa.address);
        row.put("supplier_gstin", spa.gstin);
        row.put("supplier_state", spa.state);
        row.put("supplier_state_code", spa.stateCode);
        row.put("recipient_name", guest.name);
        row.put("recipient_address", null);
        row.put("recipient_gstin", null);
        row.put("recipient_email", guest.email);
        row.put("recipient_phone", guest.phone);
        // A spa treatment is supplied where it is performed (IGST Act s.12(4)): the spa's own state,
        // so a guest's invoice is always CGST plus SGST.
        row.put("place_of_supply", spa.stateCode == null ? spa.state : GstMath.stateName(spa.stateCode));
        row.put("place_of_supply_code", spa.stateCode);
        return row;
    }

    private GstDocument save(Map<String, Object> row, Draft d, GstNumbering.Number number, LocalDateTime now, LocalDate today) {
        row.put("document_number", number.documentNumber());
        row.put("financial_year", number.financialYear());
        row.put("series", String.valueOf(number.series()));
        row.put("sequence_number", number.sequence());
        row.put("issue_date", today);
        row.put("issued_at", now);
        row.put("source_key", d.sourceKey);
        row.put("spa_id", d.spaId);
        row.put("user_id", d.userId);
        row.put("booking_id", d.bookingId);
        row.put("subscription_id", d.subscriptionId);
        row.put("original_document_id", d.originalId);
        row.put("period_start", d.periodStart);
        row.put("period_end", d.periodEnd);
        row.put("description", cut(d.description, 500));
        row.put("sac_code", d.sac);
        row.put("quantity", d.quantity);
        row.put("currency", d.currency);
        row.put("gross_cents", d.gross);
        row.put("discount_cents", d.discount);
        row.put("taxable_cents", d.taxable);
        row.put("rate_bps", d.rateBps);
        row.put("cgst_cents", d.split.cgst());
        row.put("sgst_cents", d.split.sgst());
        row.put("igst_cents", d.split.igst());
        row.put("total_cents", d.taxable + d.split.total());
        row.put("commission_base_cents", d.commissionBase);
        row.put("commission_pct", d.commissionPct);
        row.put("notes", cut(d.notes, 500));
        row.put("created_at", now);
        long id = insert.executeAndReturnKey(row).longValue();
        log.info("event=gst.document.issued type={} number={} spa={} source={}", row.get("doc_type"), number.documentNumber(), d.spaId, d.sourceKey);
        return find(id).orElseThrow();
    }

    // ------------------------------------------------------------------ parties

    record Supplier(long id, String name, String legalName, String gstin, String address, String state, String stateCode,
                    String email, String phone) {}

    record Recipient(String name, String email, String phone) {}

    private Supplier spa(long spaId) {
        var s = db.queryForMap("""
                SELECT id,name,legal_name,gstin,address_line1,address_line2,locality,city,state,postal_code,formatted_address,email,phone
                FROM spa WHERE id=?""", spaId);
        String gstin = GstMath.normalizeGstin(str(s.get("gstin")));
        String formatted = str(s.get("formatted_address"));
        String address = !blank(formatted) ? formatted : Stream.of("address_line1", "address_line2", "locality", "city", "state", "postal_code")
                .map(c -> str(s.get(c))).filter(v -> !blank(v)).collect(Collectors.joining(", "));
        // A registered spa's state is the one in its GSTIN.
        String code = gstin != null ? gstin.substring(0, 2) : GstMath.stateCode(str(s.get("state")));
        String state = code != null ? GstMath.stateName(code) : str(s.get("state"));
        String name = str(s.get("name"));
        String legal = blank(str(s.get("legal_name"))) ? name : str(s.get("legal_name")).trim();
        return new Supplier(spaId, name, legal, gstin, cut(address, 600), state, code, str(s.get("email")), str(s.get("phone")));
    }

    private Recipient guest(long spaId, long userId) {
        var u = db.queryForList("SELECT name,last_name,display_name,email,phone FROM app_user WHERE id=?", userId);
        // A guest booked in at the desk is named as the desk recorded them.
        var desk = db.queryForList("SELECT guest_name,contact FROM desk_guest WHERE spa_id=? AND user_id=?", spaId, userId);
        if (!desk.isEmpty()) {
            String contact = str(desk.get(0).get("contact"));
            boolean email = contact != null && contact.contains("@");
            return new Recipient(str(desk.get(0).get("guest_name")), email ? contact : null, email ? null : contact);
        }
        if (u.isEmpty()) return new Recipient("Guest", null, null);
        var r = u.get(0);
        String name = Stream.of(str(r.get("name")), str(r.get("last_name"))).filter(v -> !blank(v)).collect(Collectors.joining(" "));
        if (blank(name)) name = blank(str(r.get("display_name"))) ? "Guest" : str(r.get("display_name"));
        return new Recipient(name, str(r.get("email")), str(r.get("phone")));
    }

    /** The service's own rate, else the spa's tax_percent, else the configured default. */
    private int ratePct(Object serviceRate, long spaId) {
        if (serviceRate instanceof BigDecimal r) return r.intValue();
        Integer spa = db.queryForObject("SELECT tax_percent FROM spa WHERE id=?", Integer.class, spaId);
        return spa != null ? spa : defaultRatePct.intValue();
    }

    /**
     * The rate behind the tax the booking was charged. Normally the configured rate; if the spa has
     * changed its rate since, the standard rate that gives that tax.
     */
    static int rateCharged(long taxable, long tax, int configuredPct) {
        int configured = configuredPct * 100;
        if (taxable <= 0 || GstMath.taxOnExclusive(taxable, configured) == tax) return configured;
        for (int r : STANDARD_RATES) if (GstMath.taxOnExclusive(taxable, r) == tax) return r;
        return configured;
    }

    private static String rupees(long paise) {
        var f = java.text.NumberFormat.getNumberInstance(new Locale("en", "IN"));
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return "INR " + f.format(paise / 100.0);
    }

    private static long num(Object v) {
        return v == null ? 0 : ((Number) v).longValue();
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
