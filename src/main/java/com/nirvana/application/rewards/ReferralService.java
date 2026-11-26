package com.nirvana.application.rewards;

import com.nirvana.application.model.Coupon;
import com.nirvana.application.model.enums.DiscountType;
import com.nirvana.application.repository.CouponRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Refer a friend. Each customer has a code; a new customer adds it (from the link or by hand) and
 * when their first paid booking is completed both are rewarded. Rules against abuse:
 * <ul>
 *   <li>the friend needs a verified phone, and each phone number is rewarded once ever;</li>
 *   <li>no self-referral: not your own account, and not an account with your phone number;</li>
 *   <li>a referrer is rewarded for at most {@code referral.monthly-cap} friends per month;</li>
 *   <li>a code can only be added by a new account, before it has completed a booking.</li>
 * </ul>
 */
@Service
@Slf4j
public class ReferralService {

    public enum Status { PENDING, REWARDED, REJECTED }

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final JdbcTemplate db;
    private final RewardRules rules;
    private final LoyaltyLedger ledger;
    private final CouponRepository coupons;
    private final String frontendUrl;

    public ReferralService(JdbcTemplate db, RewardRules rules, LoyaltyLedger ledger, CouponRepository coupons,
                           @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.db = db;
        this.rules = rules;
        this.ledger = ledger;
        this.coupons = coupons;
        this.frontendUrl = frontendUrl;
    }

    public record Summary(String code, String link, String reward, int invited, int rewarded, int monthlyCap,
                          String referredBy, String myStatus, String myStatusReason, boolean canClaim) {
    }

    /** The customer's referral code, created the first time it is asked for. */
    @Transactional
    public String codeFor(long userId) {
        for (int attempt = 0; attempt < 10; attempt++) {
            List<String> existing = db.queryForList("SELECT referral_code FROM app_user WHERE id=?", String.class, userId);
            if (existing.isEmpty()) throw new EntityNotFoundException("User not found");
            if (existing.get(0) != null && !existing.get(0).isBlank()) return existing.get(0);
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < 8; i++) code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            try {
                db.update("UPDATE app_user SET referral_code=? WHERE id=? AND referral_code IS NULL", code.toString(), userId);
            } catch (DuplicateKeyException taken) {
                // another account has it; try another
            }
        }
        throw new IllegalStateException("Could not create a referral code");
    }

    @Transactional
    public Summary summary(long userId) {
        String code = codeFor(userId);
        Map<String, Object> counts = db.queryForMap("SELECT COUNT(*) AS invited, COALESCE(SUM(status='REWARDED' AND (referrer_points>0 OR referrer_coupon IS NOT NULL)),0) AS rewarded"
                + " FROM referral WHERE referrer_id=?", userId);
        List<Map<String, Object>> mine = db.queryForList("SELECT r.status,r.reason,r.code FROM referral r WHERE r.referee_id=?", userId);
        String referredBy = mine.isEmpty() ? null : (String) mine.get(0).get("code");
        String reason = mine.isEmpty() ? null : (String) mine.get(0).get("reason");
        return new Summary(code, link(code), rewardText(), ((Number) counts.get("invited")).intValue(),
                ((Number) counts.get("rewarded")).intValue(), rules.referralMonthlyCap, referredBy,
                mine.isEmpty() ? null : (String) mine.get(0).get("status"), reason, mine.isEmpty() && claimProblem(userId) == null);
    }

    public String link(String code) {
        return frontendUrl.replaceAll("/+$", "") + "/auth/register?ref=" + code;
    }

    public String rewardText() {
        return rules.referralReward == RewardRules.ReferralReward.COUPON
                ? "You and your friend each get a Rs " + rules.referralCouponPaise / 100 + " coupon when their first paid booking is completed."
                : "You get " + rules.referrerPoints + " points and your friend gets " + rules.refereePoints
                + " points when their first paid booking is completed.";
    }

    /** A new customer adds the code of the person who referred them. */
    @Transactional
    public Summary claim(long refereeId, String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty()) throw rejected("Enter a referral code.");
        db.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE", Long.class, refereeId);
        if (db.queryForObject("SELECT COUNT(*) FROM referral WHERE referee_id=?", Integer.class, refereeId) > 0) {
            throw rejected("You have already added a referral code.");
        }
        List<Long> referrers = db.queryForList("SELECT id FROM app_user WHERE referral_code=?", Long.class, code);
        if (referrers.isEmpty()) throw rejected("That referral code is not valid.");
        long referrerId = referrers.get(0);
        if (referrerId == refereeId) throw rejected("You can't use your own referral code.");
        String referrerPhone = phoneOf(referrerId), refereePhone = phoneOf(refereeId);
        if (referrerPhone != null && referrerPhone.equals(refereePhone)) throw rejected("You can't use your own referral code.");
        String problem = claimProblem(refereeId);
        if (problem != null) throw rejected(problem);
        db.update("INSERT INTO referral(referrer_id,referee_id,code,status,created_at) VALUES(?,?,?,'PENDING',UTC_TIMESTAMP(6))",
                referrerId, refereeId, code);
        db.update("UPDATE app_user SET referred_by=? WHERE id=?", code, refereeId);
        return summary(refereeId);
    }

    private String claimProblem(long refereeId) {
        Map<String, Object> user = db.queryForMap("SELECT created_at FROM app_user WHERE id=?", refereeId);
        Object created = user.get("created_at");
        if (created != null && LoyaltyLedger.utc(created).isBefore(Instant.now().minusSeconds(86_400L * rules.referralClaimWindowDays))) {
            return "Referral codes can only be added within " + rules.referralClaimWindowDays + " days of joining.";
        }
        if (db.queryForObject("SELECT COUNT(*) FROM booking WHERE user_id=? AND status='COMPLETED'", Integer.class, refereeId) > 0) {
            return "Referral codes can only be added before your first appointment.";
        }
        return null;
    }

    /**
     * Called when a booking that was paid for is completed. Rewards a pending referral of that
     * customer if every rule holds. Needs the transaction of the completion.
     */
    public void onPaidBookingCompleted(long refereeId, long bookingId) {
        List<Map<String, Object>> rows = db.queryForList("SELECT id,referrer_id,status FROM referral WHERE referee_id=? FOR UPDATE", refereeId);
        if (rows.isEmpty() || !Status.PENDING.name().equals(rows.get(0).get("status"))) return;
        long id = ((Number) rows.get(0).get("id")).longValue();
        long referrerId = ((Number) rows.get(0).get("referrer_id")).longValue();

        Map<String, Object> referee = db.queryForMap("SELECT phone,phone_verified FROM app_user WHERE id=?", refereeId);
        String phone = normalizePhone((String) referee.get("phone"));
        if (phone == null || !truthy(referee.get("phone_verified"))) {
            // Stays pending: rewarded once they verify a phone and complete another paid booking.
            db.update("UPDATE referral SET reason=? WHERE id=?", "Waiting for a verified phone number", id);
            return;
        }
        if (phone.equals(phoneOf(referrerId))) {
            reject(id, "Same phone number as the referrer");
            return;
        }
        if (db.queryForObject("SELECT COUNT(*) FROM referral WHERE rewarded_phone=?", Integer.class, phone) > 0) {
            reject(id, "This phone number has already earned a referral reward");
            return;
        }
        // Lock the referrer so two friends completing at once cannot both pass the monthly cap.
        db.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE", Long.class, referrerId);
        boolean referrerCapped = rewardedThisMonth(referrerId) >= rules.referralMonthlyCap;
        try {
            // Claims the phone number first: the unique key settles a race between two accounts.
            db.update("UPDATE referral SET status='REWARDED', reason=?, rewarded_phone=?, booking_id=?, reward_type=?, rewarded_at=UTC_TIMESTAMP(6) WHERE id=?",
                    referrerCapped ? "Referrer reached this month's limit" : null, phone, bookingId, rules.referralReward.name(), id);
        } catch (DuplicateKeyException taken) {
            reject(id, "This phone number has already earned a referral reward");
            return;
        }

        int referrerPoints = 0, refereePoints = 0;
        String referrerCoupon = null, refereeCoupon = null;
        if (rules.referralReward == RewardRules.ReferralReward.COUPON) {
            refereeCoupon = coupon(refereeId, id);
            if (!referrerCapped) referrerCoupon = coupon(referrerId, id);
        } else {
            refereePoints = credit(refereeId, bookingId, rules.refereePoints, "referral:" + id + ":referee", "Welcome reward for joining with a referral");
            if (!referrerCapped) {
                referrerPoints = credit(referrerId, bookingId, rules.referrerPoints, "referral:" + id + ":referrer", "Reward for referring a friend");
            }
        }
        db.update("UPDATE referral SET referrer_points=?, referee_points=?, referrer_coupon=?, referee_coupon=? WHERE id=?",
                referrerPoints, refereePoints, referrerCoupon, refereeCoupon, id);
        log.info("Referral {} rewarded (referrer capped: {})", id, referrerCapped);
    }

    private int credit(long userId, long bookingId, int points, String ref, String note) {
        if (points <= 0) return 0;
        ledger.lock(userId);
        return ledger.credit(userId, bookingId, LoyaltyLedger.Type.REFERRAL, points, ledger.standardExpiry(), ref, note, null);
    }

    private String coupon(long ownerId, long referralId) {
        StringBuilder code = new StringBuilder("REF-");
        for (int i = 0; i < 8; i++) code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        Coupon coupon = new Coupon();
        coupon.setCode(code.toString());
        coupon.setDiscountType(DiscountType.FLAT);
        coupon.setDiscountValue(rules.referralCouponPaise);
        coupon.setMaxUsesGlobal(1);
        coupon.setMaxUsesPerUser(1);
        coupon.setValidFrom(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        coupon.setValidTo(OffsetDateTime.now(ZoneOffset.UTC).plusDays(rules.referralCouponDays));
        coupon.setActive(true);
        coupon.setMetaJson("{\"ownerUserId\":" + ownerId + ",\"referralId\":" + referralId + "}");
        coupons.save(coupon);
        return coupon.getCode();
    }

    /** Referrers rewarded since the start of this month, India time. */
    int rewardedThisMonth(long referrerId) {
        LocalDateTime monthStart = Instant.now().atZone(INDIA).withDayOfMonth(1).toLocalDate().atStartOfDay(INDIA)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        Integer n = db.queryForObject("SELECT COUNT(*) FROM referral WHERE referrer_id=? AND status='REWARDED' AND rewarded_at>=?"
                + " AND (referrer_points>0 OR referrer_coupon IS NOT NULL)", Integer.class, referrerId, Timestamp.valueOf(monthStart));
        return n == null ? 0 : n;
    }

    private void reject(long id, String reason) {
        db.update("UPDATE referral SET status='REJECTED', reason=? WHERE id=?", reason, id);
        log.info("Referral {} not rewarded: {}", id, reason);
    }

    private String phoneOf(long userId) {
        List<String> phones = db.queryForList("SELECT phone FROM app_user WHERE id=?", String.class, userId);
        return phones.isEmpty() ? null : normalizePhone(phones.get(0));
    }

    /** The last ten digits, so +91 98…, 098… and 98… are one number. */
    static String normalizePhone(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 10) return digits.isEmpty() ? null : digits;
        return digits.substring(digits.length() - 10);
    }

    private static boolean truthy(Object value) {
        return value instanceof Boolean b ? b : value instanceof Number n && n.intValue() != 0;
    }

    private static ResponseStatusException rejected(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
