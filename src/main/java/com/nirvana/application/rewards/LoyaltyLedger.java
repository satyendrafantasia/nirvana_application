package com.nirvana.application.rewards;

import com.nirvana.application.model.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * The points ledger. Every change to a balance is one row in loyalty_ledger, written while the
 * customer's app_user row is locked, and app_user.loyalty_points is updated in the same step.
 * Credits are lots with an expiry; spending uses the lots that expire first.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoyaltyLedger {

    public enum Type { EARN, EARN_REVERSAL, REDEEM, REDEEM_RETURN, REFERRAL, EXPIRY, ADJUSTMENT, MIGRATION }

    /** Points handed back after a cancellation stay usable for at least this long. */
    static final int RETURN_GRACE_DAYS = 30;

    private final JdbcTemplate db;
    private final EntityManager em;
    private final RewardRules rules;

    public record Entry(long id, String type, int points, int balanceAfter, Long bookingId, String bookingReference,
                        Instant expiresAt, int remaining, String note, Instant createdAt) {
    }

    /** Locks the customer's balance for this transaction, expires due lots, and returns the balance. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int lock(long userId) {
        db.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE", Long.class, userId);
        expireDueLocked(userId);
        return balanceOf(userId);
    }

    /** Points that can be spent now (unexpired, unspent). Reads without locking. */
    public int spendable(long userId) {
        Integer points = db.queryForObject("SELECT COALESCE(SUM(remaining),0) FROM loyalty_ledger WHERE user_id=? AND remaining>0"
                + " AND (expires_at IS NULL OR expires_at>UTC_TIMESTAMP(6))", Integer.class, userId);
        return points == null ? 0 : points;
    }

    public boolean hasEntry(String refKey) {
        return refKey != null && db.queryForObject("SELECT COUNT(*) FROM loyalty_ledger WHERE ref_key=?", Integer.class, refKey) > 0;
    }

    /** Adds a lot. Returns 0 without writing when refKey was already used. Needs {@link #lock}. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int credit(long userId, Long bookingId, Type type, int points, Instant expiresAt, String refKey, String note, Long actor) {
        if (points <= 0 || hasEntry(refKey)) return 0;
        int balance = balanceOf(userId) + points;
        insert(userId, bookingId, type, points, balance, points, expiresAt, refKey, note, actor);
        setBalance(userId, balance);
        return points;
    }

    public Instant standardExpiry() {
        return Instant.now().atOffset(ZoneOffset.UTC).plusMonths(Math.max(1, rules.expiryMonths)).toInstant();
    }

    /**
     * Takes points from the lots that expire first. With {@code strict} the full amount must be
     * there; otherwise as much as there is is taken. Returns the points taken. Needs {@link #lock}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int debit(long userId, Long bookingId, Type type, int points, boolean strict, String refKey, String note, Long actor) {
        if (points <= 0 || hasEntry(refKey)) return 0;
        int balance = balanceOf(userId);
        if (strict && balance < points) {
            throw new IllegalStateException("Your points balance has changed. Review your points and try again.");
        }
        int wanted = Math.min(points, Math.max(0, balance));
        int left = wanted;
        Instant firstExpiry = null;
        List<Map<String, Object>> lots = db.queryForList("SELECT id,remaining,expires_at FROM loyalty_ledger WHERE user_id=? AND remaining>0"
                + " ORDER BY expires_at IS NULL, expires_at, id", userId);
        for (Map<String, Object> lot : lots) {
            if (left <= 0) break;
            int take = Math.min(left, ((Number) lot.get("remaining")).intValue());
            db.update("UPDATE loyalty_ledger SET remaining=remaining-? WHERE id=?", take, lot.get("id"));
            if (firstExpiry == null && lot.get("expires_at") != null) firstExpiry = utc(lot.get("expires_at"));
            left -= take;
        }
        int taken = wanted - left;
        String fullNote = taken < points && note != null ? note + " (" + (points - taken) + " could not be recovered)" : note;
        if (taken <= 0 && points > 0 && !strict) {
            insert(userId, bookingId, type, 0, balance, 0, null, refKey, fullNote, actor);
            return 0;
        }
        // A debit remembers the earliest expiry it used, so points handed back keep roughly their age.
        insert(userId, bookingId, type, -taken, balance - taken, 0, firstExpiry, refKey, fullNote, actor);
        setBalance(userId, balance - taken);
        return taken;
    }

    /** Expires lots past their date for one customer. Needs {@link #lock}. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int expireDueLocked(long userId) {
        List<Map<String, Object>> due = db.queryForList("SELECT id,remaining FROM loyalty_ledger WHERE user_id=? AND remaining>0"
                + " AND expires_at IS NOT NULL AND expires_at<=UTC_TIMESTAMP(6) ORDER BY expires_at, id", userId);
        int expired = 0;
        int balance = balanceOf(userId);
        for (Map<String, Object> lot : due) {
            int points = ((Number) lot.get("remaining")).intValue();
            db.update("UPDATE loyalty_ledger SET remaining=0 WHERE id=?", lot.get("id"));
            int take = Math.min(points, balance);
            balance -= take;
            expired += take;
            insert(userId, null, Type.EXPIRY, -take, balance, 0, null, "expiry:" + lot.get("id"), "Points expired", null);
        }
        if (expired > 0) setBalance(userId, balance);
        return expired;
    }

    /** Customers with lots that are past their date. */
    public List<Long> usersWithDueLots(int limit) {
        return db.queryForList("SELECT DISTINCT user_id FROM loyalty_ledger WHERE remaining>0 AND expires_at IS NOT NULL"
                + " AND expires_at<=UTC_TIMESTAMP(6) LIMIT ?", Long.class, limit);
    }

    /** The expiry recorded on the redemption of a booking, used when its points are handed back. */
    public Instant returnExpiry(long bookingId) {
        List<Object> found = db.queryForList("SELECT expires_at FROM loyalty_ledger WHERE booking_id=? AND entry_type='REDEEM'"
                + " ORDER BY id DESC LIMIT 1", Object.class, bookingId);
        Instant floor = Instant.now().plus(RETURN_GRACE_DAYS, ChronoUnit.DAYS);
        Instant recorded = found.isEmpty() || found.get(0) == null ? standardExpiry() : utc(found.get(0));
        return recorded.isBefore(floor) ? floor : recorded;
    }

    public int balanceOf(long userId) {
        Integer points = db.queryForObject("SELECT loyalty_points FROM app_user WHERE id=?", Integer.class, userId);
        return points == null ? 0 : points;
    }

    public List<Entry> history(long userId, int limit) {
        return db.query("SELECT l.id,l.entry_type,l.points,l.balance_after,l.booking_id,b.booking_reference,l.expires_at,l.remaining,l.note,l.created_at"
                        + " FROM loyalty_ledger l LEFT JOIN booking b ON b.id=l.booking_id WHERE l.user_id=? ORDER BY l.id DESC LIMIT ?",
                (rs, i) -> new Entry(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4),
                        (Long) rs.getObject(5, Long.class), rs.getString(6),
                        rs.getTimestamp(7) == null ? null : utc(rs.getTimestamp(7)), rs.getInt(8), rs.getString(9),
                        utc(rs.getTimestamp(10))), userId, limit);
    }

    /** Points that expire within the given days, and the first date among them. */
    public Map<String, Object> expiringWithin(long userId, int days) {
        return db.queryForMap("SELECT COALESCE(SUM(remaining),0) AS points, MIN(expires_at) AS firstAt FROM loyalty_ledger"
                + " WHERE user_id=? AND remaining>0 AND expires_at IS NOT NULL AND expires_at>UTC_TIMESTAMP(6)"
                + " AND expires_at<=UTC_TIMESTAMP(6) + INTERVAL ? DAY", userId, days);
    }

    private void insert(long userId, Long bookingId, Type type, int points, int balanceAfter, int remaining,
                        Instant expiresAt, String refKey, String note, Long actor) {
        db.update("INSERT INTO loyalty_ledger(user_id,booking_id,entry_type,points,balance_after,remaining,expires_at,ref_key,note,actor_id,created_at)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",
                userId, bookingId, type.name(), points, balanceAfter, remaining,
                expiresAt == null ? null : Timestamp.valueOf(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC)),
                refKey, note == null ? null : note.substring(0, Math.min(255, note.length())), actor);
    }

    private void setBalance(long userId, int balance) {
        db.update("UPDATE app_user SET loyalty_points=? WHERE id=?", balance, userId);
        syncLoadedUser(userId, balance);
    }

    /** Keeps a loaded User in step, so a later flush of that entity cannot write an old balance back. */
    private void syncLoadedUser(long userId, int balance) {
        try {
            User user = em.getReference(User.class, userId);
            if (em.contains(user) && org.hibernate.Hibernate.isInitialized(user)) {
                user.setLoyaltyPoints(balance);
            }
        } catch (RuntimeException ignored) {
            // nothing loaded
        }
    }

    static Instant utc(Object stored) {
        if (stored instanceof Timestamp t) return t.toLocalDateTime().toInstant(ZoneOffset.UTC);
        if (stored instanceof LocalDateTime t) return t.toInstant(ZoneOffset.UTC);
        if (stored instanceof java.time.OffsetDateTime t) return t.toInstant();
        if (stored instanceof Instant t) return t;
        throw new IllegalArgumentException("Unexpected time value " + stored);
    }
}
