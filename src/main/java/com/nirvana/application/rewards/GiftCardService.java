package com.nirvana.application.rewards;

import com.nirvana.application.config.RazorpayProperties;
import com.nirvana.application.model.dto.RazorpayConfirmRequest;
import com.nirvana.application.otp.sender.EmailSender;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Gift cards: a prepaid balance behind a code, bought online (Razorpay, like spa packages) or
 * issued by an administrator. The recipient gets the code by email and spends it at checkout in
 * one or more goes. Every balance change is a gift_card_txn row written under a lock on the card.
 */
@Service
@Slf4j
public class GiftCardService {

    public enum Status { PAYMENT_PENDING, ACTIVE, EXHAUSTED, EXPIRED, VOID, REFUNDED }

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final String COLUMNS = "g.id,g.code,g.status,g.source,g.spa_id,s.name AS spa_name,g.initial_cents,g.balance_cents,g.currency,"
            + "g.recipient_name,g.recipient_email,g.message,g.expires_at,g.activated_at,g.created_at,g.purchaser_id";

    private final JdbcTemplate db;
    private final RewardRules rules;
    private final RazorpayProperties props;
    private final RazorpayClient gateway;
    private final ObjectProvider<EmailSender> email;
    private final String frontendUrl;

    public GiftCardService(JdbcTemplate db, RewardRules rules, RazorpayProperties props, @Nullable RazorpayClient gateway,
                           ObjectProvider<EmailSender> email, @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.db = db;
        this.rules = rules;
        this.props = props;
        this.gateway = gateway;
        this.email = email;
        this.frontendUrl = frontendUrl;
    }

    public record Card(long id, String code, String status, String source, Long spaId, String spaName, int initialCents,
                       int balanceCents, String currency, String recipientName, String recipientEmail, String message,
                       Instant expiresAt, Instant activatedAt, Instant createdAt, Long purchaserId) {
    }

    public record PurchaseInput(int amountCents, Long spaId, String recipientName, String recipientEmail, String message) {
    }

    public record IssueInput(int amountCents, Long spaId, String recipientName, String recipientEmail, String message,
                             Integer validityDays, String note) {
    }

    public record Checkout(Card card, String razorpayKeyId, String razorpayOrderId, int amountCents, String currency) {
    }

    /** What the holder of a code may see: no purchaser or recipient details. */
    public record Balance(String code, String status, int balanceCents, String currency, Long spaId, String spaName, Instant expiresAt) {
    }

    // ------------------------------------------------------------------ buying

    @Transactional
    public Checkout startPurchase(long userId, PurchaseInput input) {
        props.requireCheckout();
        requireGateway();
        validateAmount(input.amountCents());
        String recipientEmail = requireEmail(input.recipientEmail());
        Long spaId = validSpa(input.spaId());
        String code = newCode();
        db.update("INSERT INTO gift_card(code,status,source,purchaser_id,recipient_name,recipient_email,message,spa_id,initial_cents,balance_cents,"
                        + "currency,validity_days,created_at,updated_at) VALUES(?,?,'PURCHASE',?,?,?,?,?,?,0,'INR',?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                code, Status.PAYMENT_PENDING.name(), userId, trim(input.recipientName(), 100), recipientEmail, trim(input.message(), 300),
                spaId, input.amountCents(), rules.giftCardValidityDays());
        long id = db.queryForObject("SELECT id FROM gift_card WHERE code=?", Long.class, code);
        String orderId;
        try {
            orderId = gateway.orders.create(new JSONObject().put("amount", input.amountCents()).put("currency", "INR")
                    .put("receipt", "gift-card-" + id)).get("id");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to start checkout", e);
        }
        db.update("UPDATE gift_card SET razorpay_order_id=?, updated_at=UTC_TIMESTAMP(6) WHERE id=?", orderId, id);
        return new Checkout(card(id), props.getKeyId(), orderId, input.amountCents(), "INR");
    }

    /** Checks the Razorpay signature and the captured payment, then activates the card. */
    @Transactional
    public Card confirm(long userId, long id, RazorpayConfirmRequest request) {
        requireGateway();
        Card card = lockOwned(userId, id);
        String orderId = db.queryForObject("SELECT razorpay_order_id FROM gift_card WHERE id=?", String.class, id);
        if (!Objects.equals(orderId, request.getRazorpayOrderId())) throw new IllegalArgumentException("Order mismatch");
        try {
            boolean valid = Utils.verifyPaymentSignature(new JSONObject().put("razorpay_order_id", orderId)
                    .put("razorpay_payment_id", request.getRazorpayPaymentId())
                    .put("razorpay_signature", request.getRazorpaySignature()), props.getKeySecret());
            if (!valid) throw new IllegalArgumentException("Invalid signature");
            settle(card.id(), orderId, request.getRazorpayPaymentId());
        } catch (com.razorpay.RazorpayException e) {
            throw new IllegalStateException("Payment verification unavailable", e);
        }
        return card(id);
    }

    /** Looks for a captured payment on a pending purchase (for example after the browser closed). */
    @Transactional
    public Card resume(long userId, long id) {
        requireGateway();
        Card card = lockOwned(userId, id);
        if (Status.PAYMENT_PENDING.name().equals(card.status())) {
            String orderId = db.queryForObject("SELECT razorpay_order_id FROM gift_card WHERE id=?", String.class, id);
            try {
                for (com.razorpay.Payment p : gateway.orders.fetchPayments(orderId)) {
                    if ("captured".equals(p.get("status"))) {
                        settle(id, orderId, p.get("id"));
                        break;
                    }
                }
            } catch (com.razorpay.RazorpayException e) {
                throw new IllegalStateException("Unable to verify payment. Try again before paying again.", e);
            }
        }
        return card(id);
    }

    /** Webhook: payment.captured. Does nothing for orders that are not gift cards. */
    @Transactional
    public void captured(String orderId, String paymentId) {
        List<Long> ids = db.queryForList("SELECT id FROM gift_card WHERE razorpay_order_id=?", Long.class, orderId);
        if (ids.isEmpty() || gateway == null) return;
        db.queryForObject("SELECT id FROM gift_card WHERE id=? FOR UPDATE", Long.class, ids.get(0));
        try {
            settle(ids.get(0), orderId, paymentId);
        } catch (com.razorpay.RazorpayException e) {
            throw new IllegalStateException("Unable to reconcile gift card capture", e);
        }
    }

    /** Webhook: refund.processed. Whatever was refunded comes off the balance; a full refund closes the card. */
    @Transactional
    public void refunded(String paymentId) {
        List<Long> ids = db.queryForList("SELECT id FROM gift_card WHERE payment_reference_id=?", Long.class, paymentId);
        if (ids.isEmpty() || gateway == null) return;
        long id = ids.get(0);
        Map<String, Object> row = db.queryForMap("SELECT status,balance_cents,initial_cents FROM gift_card WHERE id=? FOR UPDATE", id);
        int refunded;
        try {
            refunded = ((Number) gateway.payments.fetch(paymentId).get("amount_refunded")).intValue();
        } catch (com.razorpay.RazorpayException e) {
            throw new IllegalStateException("Unable to reconcile gift card refund", e);
        }
        Integer alreadyRefunded = db.queryForObject("SELECT COALESCE(-SUM(amount_cents),0) FROM gift_card_txn WHERE gift_card_id=? AND txn_type='REFUND'",
                Integer.class, id);
        int newRefund = refunded - (alreadyRefunded == null ? 0 : alreadyRefunded);
        if (newRefund <= 0) return;
        int balance = ((Number) row.get("balance_cents")).intValue();
        int take = Math.min(balance, newRefund);
        int after = balance - take;
        // The txn records the refund in full so replays are recognised even when part was already spent.
        txn(id, null, null, "REFUND", -newRefund, after, "refund:" + paymentId + ":" + refunded, null,
                take < newRefund ? "Payment refunded; " + (newRefund - take) + " paise had already been spent" : "Payment refunded");
        String status = refunded >= ((Number) row.get("initial_cents")).intValue() ? Status.REFUNDED.name()
                : after == 0 ? Status.EXHAUSTED.name() : (String) row.get("status");
        db.update("UPDATE gift_card SET balance_cents=?, status=?, updated_at=UTC_TIMESTAMP(6) WHERE id=?", after, status, id);
    }

    private void settle(long id, String orderId, String paymentId) throws com.razorpay.RazorpayException {
        Map<String, Object> row = db.queryForMap("SELECT status,initial_cents,currency FROM gift_card WHERE id=?", id);
        com.razorpay.Payment p = gateway.payments.fetch(paymentId);
        int amount = ((Number) row.get("initial_cents")).intValue();
        if (!Objects.equals(paymentId, p.get("id")) || !Objects.equals(orderId, p.get("order_id")) || !"captured".equals(p.get("status"))
                || !Boolean.TRUE.equals(p.get("captured")) || ((Number) p.get("amount")).intValue() != amount
                || !Objects.equals(row.get("currency"), p.get("currency")) || ((Number) p.get("amount_refunded")).intValue() != 0) {
            throw new IllegalStateException("Payment has not been captured for this gift card and amount.");
        }
        if (!Status.PAYMENT_PENDING.name().equals(row.get("status"))) return;
        activate(id, amount, paymentId, null, "Bought online");
    }

    // ------------------------------------------------------------------ admin

    @Transactional
    public Card issue(long adminId, IssueInput input) {
        validateAmount(input.amountCents());
        int days = input.validityDays() == null ? rules.giftCardValidityDays()
                : Math.max(RewardRules.MIN_GIFT_CARD_VALIDITY_DAYS, input.validityDays());
        String code = newCode();
        db.update("INSERT INTO gift_card(code,status,source,recipient_name,recipient_email,message,spa_id,initial_cents,balance_cents,"
                        + "currency,validity_days,issued_by,created_at,updated_at) VALUES(?,?,'ADMIN',?,?,?,?,?,0,'INR',?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                code, Status.PAYMENT_PENDING.name(), trim(input.recipientName(), 100), requireEmail(input.recipientEmail()),
                trim(input.message(), 300), validSpa(input.spaId()), input.amountCents(), days, adminId);
        long id = db.queryForObject("SELECT id FROM gift_card WHERE code=?", Long.class, code);
        activate(id, input.amountCents(), null, adminId, input.note() == null || input.note().isBlank() ? "Issued by Nirvana" : trim(input.note(), 255));
        return card(id);
    }

    @Transactional
    public Card voidCard(long adminId, long id, String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Give a reason for voiding the gift card");
        Map<String, Object> row = lockRow(id);
        String status = (String) row.get("status");
        if (Status.VOID.name().equals(status)) return card(id);
        if (Status.PAYMENT_PENDING.name().equals(status)) throw new IllegalStateException("This gift card was never paid for");
        int balance = ((Number) row.get("balance_cents")).intValue();
        txn(id, null, null, "VOID", -balance, 0, "void:" + id, adminId, trim(reason, 255));
        db.update("UPDATE gift_card SET status='VOID', balance_cents=0, voided_at=UTC_TIMESTAMP(6), void_reason=?, updated_at=UTC_TIMESTAMP(6) WHERE id=?",
                trim(reason, 300), id);
        return card(id);
    }

    public List<Card> adminList(String query, int limit) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return db.query("SELECT " + COLUMNS + " FROM gift_card g LEFT JOIN spa s ON s.id=g.spa_id ORDER BY g.id DESC LIMIT ?", CARD, limit);
        }
        String like = "%" + q.replace("%", "").replace("_", "") + "%";
        return db.query("SELECT " + COLUMNS + " FROM gift_card g LEFT JOIN spa s ON s.id=g.spa_id WHERE g.code=? OR g.recipient_email LIKE ?"
                + " ORDER BY g.id DESC LIMIT ?", CARD, normalize(q), like, limit);
    }

    public List<Map<String, Object>> transactions(long id) {
        return db.queryForList("SELECT t.id,t.txn_type AS type,t.amount_cents AS amountCents,t.balance_after AS balanceAfter,t.booking_id AS bookingId,"
                + "b.booking_reference AS bookingReference,t.spa_id AS spaId,t.note,t.created_at AS createdAt FROM gift_card_txn t"
                + " LEFT JOIN booking b ON b.id=t.booking_id WHERE t.gift_card_id=? ORDER BY t.id DESC", id);
    }

    // ------------------------------------------------------------------ customer views

    public List<Card> purchasedBy(long userId) {
        return db.query("SELECT " + COLUMNS + " FROM gift_card g LEFT JOIN spa s ON s.id=g.spa_id WHERE g.purchaser_id=? ORDER BY g.id DESC LIMIT 100",
                CARD, userId);
    }

    public Balance balance(String code) {
        Card card = byCode(code);
        if (card == null || Status.PAYMENT_PENDING.name().equals(card.status())) throw new EntityNotFoundException("Gift card not found");
        String status = card.expiresAt() != null && !card.expiresAt().isAfter(Instant.now()) && Status.ACTIVE.name().equals(card.status())
                ? Status.EXPIRED.name() : card.status();
        int balance = Status.EXPIRED.name().equals(status) ? 0 : card.balanceCents();
        return new Balance(card.code(), status, balance, card.currency(), card.spaId(), card.spaName(), card.expiresAt());
    }

    // ------------------------------------------------------------------ checkout

    /** How much of a total the code would pay at this spa. Validates without spending. */
    public int quote(String code, long spaId, int totalPaise) {
        Card card = usable(byCode(code), spaId);
        return CheckoutMath.giftCardShare(card.balanceCents(), totalPaise);
    }

    /** Spends from the card for a booking. Returns the amount taken. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int redeem(long bookingId, long spaId, String code, int totalPaise) {
        Card found = byCode(code);
        if (found == null) throw usableProblem("Gift card code is not valid. Check it or remove it to continue.");
        lockRow(found.id());
        Card card = usable(card(found.id()), spaId);
        int amount = CheckoutMath.giftCardShare(card.balanceCents(), totalPaise);
        if (amount <= 0) return 0;
        int after = card.balanceCents() - amount;
        txn(card.id(), bookingId, spaId, "REDEEM", -amount, after, "redeem:" + bookingId, null, null);
        db.update("UPDATE gift_card SET balance_cents=?, status=?, updated_at=UTC_TIMESTAMP(6) WHERE id=?",
                after, after == 0 ? Status.EXHAUSTED.name() : Status.ACTIVE.name(), card.id());
        return amount;
    }

    public com.nirvana.application.model.dto.CancellationQuote.Tender quoteReturn(long bookingId, int percent) {
        var amounts = db.queryForList("""
             SELECT -SUM(amount_cents) FROM gift_card_txn WHERE booking_id=? AND txn_type IN ('REDEEM','REVERSAL')
             GROUP BY gift_card_id HAVING SUM(CASE WHEN txn_type='REVERSAL' THEN 1 ELSE 0 END)=0
             """, Integer.class, bookingId);
        int fee = 0, back = 0;
        for (int paid : amounts) {
            var portion = com.nirvana.application.model.dto.CancellationQuote.Tender.from(paid, percent);
            fee += portion.fee();
            back += portion.refund();
        }
        return new com.nirvana.application.model.dto.CancellationQuote.Tender(fee, back);
    }

    /** Net amount a booking took from gift cards (redeemed less returned). */
    public int netRedeemed(long bookingId) {
        Integer net = db.queryForObject("SELECT COALESCE(-SUM(amount_cents),0) FROM gift_card_txn WHERE booking_id=? AND txn_type IN ('REDEEM','REVERSAL')",
                Integer.class, bookingId);
        return net == null ? 0 : Math.max(0, net);
    }

    /**
     * Gives back {@code percent} of what a booking took from its gift card (a cancellation fee keeps
     * the rest). A card that ran out is active again; one that expired meanwhile gets 30 days.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int returnForBooking(long bookingId, int percent) {
        List<Long> cards = db.queryForList("SELECT DISTINCT gift_card_id FROM gift_card_txn WHERE booking_id=? AND txn_type='REDEEM'", Long.class, bookingId);
        int returned = 0;
        for (Long id : cards) {
            Map<String, Object> row = lockRow(id);
            Integer net = db.queryForObject("SELECT COALESCE(-SUM(amount_cents),0) FROM gift_card_txn WHERE booking_id=? AND gift_card_id=?"
                    + " AND txn_type='REDEEM' AND NOT EXISTS (SELECT 1 FROM gift_card_txn t WHERE t.booking_id=? AND t.gift_card_id=? AND t.txn_type='REVERSAL')",
                    Integer.class, bookingId, id, bookingId, id);
            int back = com.nirvana.application.model.dto.CancellationQuote.Tender.from(Math.max(0, net == null ? 0 : net), percent).refund();
            if (back <= 0) continue;
            int after = ((Number) row.get("balance_cents")).intValue() + back;
            String status = (String) row.get("status");
            String newStatus = Status.VOID.name().equals(status) || Status.REFUNDED.name().equals(status) ? status : Status.ACTIVE.name();
            txn(id, bookingId, (Long) db.queryForObject("SELECT spa_id FROM gift_card_txn WHERE booking_id=? AND gift_card_id=? AND txn_type='REDEEM' LIMIT 1",
                    Long.class, bookingId, id), "REVERSAL", back, after, "reversal:" + bookingId + ":" + id, null, "Booking cancelled");
            db.update("UPDATE gift_card SET balance_cents=?, status=?, expires_at=CASE WHEN expires_at<UTC_TIMESTAMP(6) + INTERVAL 30 DAY"
                    + " THEN UTC_TIMESTAMP(6) + INTERVAL 30 DAY ELSE expires_at END, updated_at=UTC_TIMESTAMP(6) WHERE id=?", after, newStatus, id);
            returned += back;
        }
        return returned;
    }

    // ------------------------------------------------------------------ owner

    public Map<String, Object> ownerSummary(long spaId, Instant from, Instant to) {
        Timestamp start = ts(from), end = ts(to);
        Map<String, Object> sales = db.queryForMap("SELECT COUNT(*) AS cards, COALESCE(SUM(initial_cents),0) AS soldCents,"
                + " COALESCE(SUM(CASE WHEN status IN ('ACTIVE','EXHAUSTED') THEN balance_cents ELSE 0 END),0) AS outstandingCents"
                + " FROM gift_card WHERE spa_id=? AND status NOT IN ('PAYMENT_PENDING') AND activated_at>=? AND activated_at<?", spaId, start, end);
        Map<String, Object> redeemed = db.queryForMap("SELECT COALESCE(-SUM(amount_cents),0) AS redeemedCents, COUNT(DISTINCT booking_id) AS bookings"
                + " FROM gift_card_txn WHERE spa_id=? AND txn_type IN ('REDEEM','REVERSAL') AND created_at>=? AND created_at<?", spaId, start, end);
        List<Map<String, Object>> recent = db.queryForList("SELECT t.created_at AS createdAt,t.txn_type AS type,t.amount_cents AS amountCents,"
                + "b.booking_reference AS bookingReference,CONCAT('ending ',RIGHT(g.code,4)) AS card,CASE WHEN g.spa_id IS NULL THEN 'Any spa' ELSE 'This spa' END AS scope"
                + " FROM gift_card_txn t JOIN gift_card g ON g.id=t.gift_card_id LEFT JOIN booking b ON b.id=t.booking_id"
                + " WHERE t.spa_id=? AND t.txn_type IN ('REDEEM','REVERSAL') AND t.created_at>=? AND t.created_at<? ORDER BY t.id DESC LIMIT 50", spaId, start, end);
        return Map.of("currency", "INR", "sales", sales, "redemptions", redeemed, "recent", recent);
    }

    // ------------------------------------------------------------------ expiry

    public List<Long> dueForExpiry(int limit) {
        return db.queryForList("SELECT id FROM gift_card WHERE status IN ('ACTIVE','EXHAUSTED') AND expires_at<=UTC_TIMESTAMP(6) LIMIT ?", Long.class, limit);
    }

    @Transactional
    public boolean expire(long id) {
        Map<String, Object> row = lockRow(id);
        String status = (String) row.get("status");
        if (!Status.ACTIVE.name().equals(status) && !Status.EXHAUSTED.name().equals(status)) return false;
        Instant expiresAt = row.get("expires_at") == null ? null : LoyaltyLedger.utc(row.get("expires_at"));
        if (expiresAt == null || expiresAt.isAfter(Instant.now())) return false;
        int balance = ((Number) row.get("balance_cents")).intValue();
        txn(id, null, null, "EXPIRE", -balance, 0, "expire:" + id + ":" + expiresAt.toEpochMilli(), null, "Gift card expired");
        db.update("UPDATE gift_card SET status='EXPIRED', balance_cents=0, updated_at=UTC_TIMESTAMP(6) WHERE id=?", id);
        return true;
    }

    // ------------------------------------------------------------------ internal

    private void activate(long id, int amount, String paymentId, Long actor, String note) {
        db.update("UPDATE gift_card SET status='ACTIVE', balance_cents=?, payment_reference_id=?, activated_at=UTC_TIMESTAMP(6),"
                + " expires_at=UTC_TIMESTAMP(6) + INTERVAL validity_days DAY, updated_at=UTC_TIMESTAMP(6) WHERE id=?", amount, paymentId, id);
        txn(id, null, null, "ISSUE", amount, amount, "issue:" + id, actor, note);
        sendCode(card(id));
    }

    private void sendCode(Card card) {
        EmailSender sender = email.getIfAvailable();
        if (sender == null) return;
        String from = null;
        if (card.purchaserId() != null) {
            List<String> names = db.queryForList("SELECT COALESCE(NULLIF(TRIM(CONCAT(COALESCE(name,''),' ',COALESCE(last_name,''))),''),'A friend')"
                    + " FROM app_user WHERE id=?", String.class, card.purchaserId());
            from = names.isEmpty() ? null : names.get(0);
        }
        String where = card.spaId() == null ? "at any spa on Nirvana" : "at " + card.spaName();
        StringBuilder body = new StringBuilder();
        body.append(card.recipientName() == null || card.recipientName().isBlank() ? "Hello," : "Hello " + card.recipientName() + ",").append("\n\n");
        body.append(from == null ? "You have received" : from + " has sent you").append(" a Nirvana gift card worth Rs ")
                .append(rupees(card.initialCents())).append(", to use ").append(where).append(".\n\n");
        if (card.message() != null && !card.message().isBlank()) body.append("\"").append(card.message()).append("\"\n\n");
        body.append("Your gift card code: ").append(card.code()).append("\n");
        if (card.expiresAt() != null) body.append("Valid until: ").append(DATE.format(card.expiresAt().atZone(INDIA))).append("\n");
        body.append("\nBook a treatment at ").append(frontendUrl).append(" and enter the code at checkout. ")
                .append("You can use it over several bookings until the balance runs out.\n\nKeep this code private: anyone with it can spend the balance.\n");
        try {
            sender.sendOtpEmail(card.recipientEmail(), "Your Nirvana gift card", body.toString());
            db.update("UPDATE gift_card SET email_sent_at=UTC_TIMESTAMP(6) WHERE id=?", card.id());
        } catch (RuntimeException e) {
            log.warn("Gift card {} is active but its email could not be sent: {}", card.id(), e.getMessage());
        }
    }

    private Card usable(Card card, long spaId) {
        if (card == null || Status.PAYMENT_PENDING.name().equals(card.status())) {
            throw usableProblem("Gift card code is not valid. Check it or remove it to continue.");
        }
        if (Status.VOID.name().equals(card.status()) || Status.REFUNDED.name().equals(card.status())) {
            throw usableProblem("This gift card has been cancelled.");
        }
        if (Status.EXPIRED.name().equals(card.status()) || (card.expiresAt() != null && !card.expiresAt().isAfter(Instant.now()))) {
            throw usableProblem("This gift card has expired.");
        }
        if (card.spaId() != null && card.spaId() != spaId) {
            throw usableProblem("This gift card can only be used at " + card.spaName() + ".");
        }
        if (card.balanceCents() <= 0) throw usableProblem("This gift card has no balance left.");
        return card;
    }

    /** 400, like a coupon problem: the customer fixes or removes the code. */
    private static RuntimeException usableProblem(String message) {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, message);
    }

    private void txn(long cardId, Long bookingId, Long spaId, String type, int amount, int balanceAfter, String refKey, Long actor, String note) {
        db.update("INSERT INTO gift_card_txn(gift_card_id,booking_id,spa_id,txn_type,amount_cents,balance_after,ref_key,actor_id,note,created_at)"
                + " VALUES(?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))", cardId, bookingId, spaId, type, amount, balanceAfter, refKey, actor, note);
    }

    private Map<String, Object> lockRow(long id) {
        List<Map<String, Object>> rows = db.queryForList("SELECT id,status,balance_cents,expires_at FROM gift_card WHERE id=? FOR UPDATE", id);
        if (rows.isEmpty()) throw new EntityNotFoundException("Gift card not found");
        return rows.get(0);
    }

    private Card lockOwned(long userId, long id) {
        lockRow(id);
        Card card = card(id);
        if (card.purchaserId() == null || card.purchaserId() != userId) throw new EntityNotFoundException("Gift card not found");
        return card;
    }

    public Card card(long id) {
        List<Card> found = db.query("SELECT " + COLUMNS + " FROM gift_card g LEFT JOIN spa s ON s.id=g.spa_id WHERE g.id=?", CARD, id);
        if (found.isEmpty()) throw new EntityNotFoundException("Gift card not found");
        return found.get(0);
    }

    private Card byCode(String code) {
        if (code == null || code.isBlank()) return null;
        List<Card> found = db.query("SELECT " + COLUMNS + " FROM gift_card g LEFT JOIN spa s ON s.id=g.spa_id WHERE g.code=?", CARD, normalize(code));
        return found.isEmpty() ? null : found.get(0);
    }

    /** Codes are matched without case, spaces or missing dashes: "nvg abcd efgh jkmn" works. */
    static String normalize(String code) {
        String raw = code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (raw.startsWith("NVG")) raw = raw.substring(3);
        if (raw.length() != 12) return code.trim().toUpperCase(Locale.ROOT);
        return "NVG-" + raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-" + raw.substring(8);
    }

    private String newCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder raw = new StringBuilder();
            for (int i = 0; i < 12; i++) raw.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            String code = normalize(raw.toString());
            if (db.queryForObject("SELECT COUNT(*) FROM gift_card WHERE code=?", Integer.class, code) == 0) return code;
        }
        throw new IllegalStateException("Could not create a gift card code");
    }

    private void validateAmount(int amount) {
        if (amount < rules.giftCardMinPaise || amount > rules.giftCardMaxPaise) {
            throw new IllegalArgumentException("Choose an amount between Rs " + rupees(rules.giftCardMinPaise) + " and Rs " + rupees(rules.giftCardMaxPaise) + ".");
        }
        if (amount % 100 != 0) throw new IllegalArgumentException("Choose a whole number of rupees.");
    }

    private Long validSpa(Long spaId) {
        if (spaId == null) return null;
        Integer active = db.queryForObject("SELECT COUNT(*) FROM spa WHERE id=? AND is_active=1", Integer.class, spaId);
        if (active == null || active == 0) throw new IllegalArgumentException("That spa is not available for gift cards.");
        return spaId;
    }

    private static String requireEmail(String email) {
        String value = email == null ? "" : email.trim();
        if (!value.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") || value.length() > 255) {
            throw new IllegalArgumentException("Enter the recipient's email address.");
        }
        return value;
    }

    private void requireGateway() {
        if (!props.isEnabled() || gateway == null) {
            throw new IllegalStateException("Online gift card payments are not available yet. Please contact Nirvana.");
        }
    }

    private static String trim(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String t = value.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static String rupees(int paise) {
        return paise % 100 == 0 ? String.format(Locale.ENGLISH, "%,d", paise / 100) : String.format(Locale.ENGLISH, "%,.2f", paise / 100.0);
    }

    private static Timestamp ts(Instant at) {
        return Timestamp.valueOf(LocalDateTime.ofInstant(at, ZoneOffset.UTC));
    }

    private static final RowMapper<Card> CARD = (rs, i) -> new Card(rs.getLong("id"), rs.getString("code"), rs.getString("status"),
            rs.getString("source"), (Long) rs.getObject("spa_id", Long.class), rs.getString("spa_name"), rs.getInt("initial_cents"),
            rs.getInt("balance_cents"), rs.getString("currency"), rs.getString("recipient_name"), rs.getString("recipient_email"),
            rs.getString("message"), at(rs.getTimestamp("expires_at")), at(rs.getTimestamp("activated_at")), at(rs.getTimestamp("created_at")),
            (Long) rs.getObject("purchaser_id", Long.class));

    private static Instant at(Timestamp t) {
        return t == null ? null : t.toLocalDateTime().toInstant(ZoneOffset.UTC);
    }

    static Instant inDays(int days) {
        return Instant.now().plus(days, ChronoUnit.DAYS);
    }
}
