package com.nirvana.application.service.erasure;

import com.nirvana.application.auth.service.OtpAuthService;
import com.nirvana.application.model.User;
import com.nirvana.application.repository.UserRepository;
import com.nirvana.application.security.RefreshTokenService;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The data principal's rights under India's DPDP Act 2023: a copy of their data, and erasure.
 *
 * <p>Deletion is asked for with a fresh proof (password, or a one-time code / Firebase phone
 * proof), then waits out a grace period during which signing in cancels it. After that the
 * {@link AccountErasureJob} erases the person: contact details, sign-in identities, sessions,
 * preferences and free text they wrote. Booking, payment, invoice and refund rows stay, because
 * tax and accounting law requires them, but they then point only at an account row with no
 * personal data left on it.
 */
@Service
@Slf4j
public class AccountErasureService {

    private static final List<String> UPCOMING = List.of("CONFIRMED", "ARRIVED");

    private final JdbcTemplate db;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final OtpAuthService otp;
    private final RefreshTokenService refreshTokens;
    private final AccountErasureLog audit;
    private final TransactionTemplate eachAccount;
    private final int graceDays;

    public AccountErasureService(JdbcTemplate db, UserRepository users, PasswordEncoder passwordEncoder,
                                 OtpAuthService otp, RefreshTokenService refreshTokens, AccountErasureLog audit,
                                 PlatformTransactionManager transactions,
                                 @Value("${app.account-erasure.grace-days:7}") int graceDays) {
        this.db = db;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.otp = otp;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.eachAccount = new TransactionTemplate(transactions);
        this.graceDays = graceDays;
    }

    public record Status(boolean pending, OffsetDateTime requestedAt, OffsetDateTime eraseAfter, int graceDays,
                         List<String> blockers) {
    }

    /** One reason deletion cannot go ahead yet: a code for the job's audit log, a message for the person. */
    record Blocker(String code, String message) {
    }

    public int graceDays() {
        return graceDays;
    }

    @Transactional(readOnly = true)
    public Status status(long userId) {
        User user = find(userId);
        return status(user, blockers(userId).stream().map(Blocker::message).toList());
    }

    /**
     * Starts the grace period. {@code loginToken} is the proof from the sign-in code flow
     * (a typed code or Firebase's phone proof), for accounts without a password they know.
     */
    @Transactional
    public Status request(long userId, String password, String loginToken) {
        User user = find(userId);
        if (loginToken != null && !loginToken.isBlank()) {
            if (!otp.consumeLoginProof(loginToken.trim()).getId().equals(userId))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verify the contact on this account.");
        } else if (password != null && !password.isEmpty()) {
            if (!passwordEncoder.matches(password, user.getPassword()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your password is incorrect.");
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Confirm with your password or a verification code.");
        }
        List<Blocker> blockers = blockers(userId);
        if (!blockers.isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    String.join(" ", blockers.stream().map(Blocker::message).toList()));
        if (user.getDataErasureRequestedAt() == null) {
            OffsetDateTime now = OffsetDateTime.now();
            user.setDataErasureRequestedAt(now);
            // End every session, including this one: signing in again is what cancels the request.
            user.setCredentialsChangedAt(now);
            users.save(user);
            audit.record(userId, AccountErasureLog.REQUESTED, "grace period " + graceDays + " days");
        }
        refreshTokens.revokeAll(userId);
        return status(user, List.of());
    }

    private Status status(User user, List<String> blockers) {
        OffsetDateTime requested = user.getDataErasureRequestedAt();
        return new Status(requested != null, requested, requested == null ? null : requested.plusDays(graceDays),
                graceDays, blockers);
    }

    List<Blocker> blockers(long userId) {
        List<Blocker> found = new ArrayList<>();
        Integer upcoming = db.queryForObject("SELECT COUNT(*) FROM booking WHERE user_id = ? AND status IN ('CONFIRMED','ARRIVED') "
                + "AND end_ts > ?", Integer.class, userId, Timestamp.from(Instant.now()));
        if (upcoming != null && upcoming > 0)
            found.add(new Blocker("upcoming_bookings", upcoming == 1
                    ? "You have an upcoming booking. Cancel it or wait until it is over, then delete your account."
                    : "You have " + upcoming + " upcoming bookings. Cancel them or wait until they are over, then delete your account."));
        Integer refunds = db.queryForObject("SELECT COUNT(*) FROM booking b WHERE b.user_id = ? AND ("
                + "b.refund_status IN ('REQUESTED','APPROVED','PROCESSING') OR EXISTS (SELECT 1 FROM payment_refund r "
                + "WHERE r.booking_id = b.id AND r.status IN ('REQUESTED','APPROVED','PROCESSING')))", Integer.class, userId);
        if (refunds != null && refunds > 0)
            found.add(new Blocker("active_refund",
                    "A refund to you is still being processed. You can delete your account once it is complete."));
        Integer spas = db.queryForObject("SELECT COUNT(*) FROM spa s JOIN spa_manager m ON m.id = s.spa_manager_id "
                + "WHERE m.user_id = ? AND s.is_active = 1", Integer.class, userId);
        if (spas != null && spas > 0)
            found.add(new Blocker("owns_open_spas", (spas == 1 ? "You manage a spa that is still open." : "You manage " + spas + " spas that are still open.")
                    + " Transfer each spa to another owner or close it first."));
        return found;
    }

    /** Ids of accounts whose grace period has ended, oldest first. */
    public List<Long> due(Instant now) {
        return db.queryForList("SELECT id FROM app_user WHERE data_erased_at IS NULL AND data_erasure_requested_at IS NOT NULL "
                        + "AND data_erasure_requested_at <= ? ORDER BY data_erasure_requested_at LIMIT 200", Long.class,
                Timestamp.from(now.minus(Duration.ofDays(graceDays))));
    }

    /** Erases every account whose grace period has ended; each account in its own transaction. */
    public int eraseDue() {
        int erased = 0;
        Instant now = Instant.now();
        for (Long id : due(now)) {
            try {
                if (Boolean.TRUE.equals(eachAccount.execute(tx -> eraseIfStillDue(id, now)))) erased++;
            } catch (RuntimeException failure) {
                // Logged without the exception message, which could quote personal data from SQL.
                log.error("Account erasure failed for account {}: {}", id, failure.getClass().getSimpleName());
            }
        }
        return erased;
    }

    private boolean eraseIfStillDue(long id, Instant now) {
        // Locks the row, so two instances running the job cannot erase the same account twice,
        // and a sign-in that cancelled the request in the meantime wins.
        List<Map<String, Object>> rows = db.queryForList("SELECT phone, email FROM app_user WHERE id = ? AND data_erased_at IS NULL "
                + "AND data_erasure_requested_at IS NOT NULL AND data_erasure_requested_at <= ? FOR UPDATE",
                id, Timestamp.from(now.minus(Duration.ofDays(graceDays))));
        if (rows.isEmpty()) return false;
        List<Blocker> blockers = blockers(id);
        if (!blockers.isEmpty()) {
            audit.record(id, AccountErasureLog.DEFERRED, String.join(",", blockers.stream().map(Blocker::code).toList()));
            return false;
        }
        String phone = (String) rows.get(0).get("phone");
        String email = (String) rows.get(0).get("email");
        Timestamp at = Timestamp.from(now);

        // Sessions, sign-in identities, devices, codes and preferences: deleted outright.
        db.update("DELETE FROM refresh_token WHERE user_id = ?", id);
        db.update("DELETE FROM customer_google_identity WHERE user_id = ?", id);
        db.update("DELETE FROM owner_push_device WHERE user_id = ?", id);
        db.update("DELETE FROM otp_verification WHERE user_id = ? OR (phone_number IS NOT NULL AND phone_number = ?) "
                + "OR (email IS NOT NULL AND email = ?)", id, phone, email);
        db.update("DELETE FROM listing_claim_phone_check WHERE user_id = ?", id);
        db.update("DELETE FROM notifications WHERE recipient_user_id = ?", id);
        db.update("DELETE FROM notification_log WHERE user_id = ?", id);
        db.update("DELETE FROM user_favorite_spa WHERE user_id = ?", id);
        db.update("DELETE FROM user_favorite_therapist WHERE user_id = ?", id);
        db.update("DELETE FROM waitlist_entry WHERE user_id = ?", id);
        db.update("DELETE FROM booking_hold WHERE user_id = ?", id);
        db.update("DELETE FROM desk_guest WHERE user_id = ?", id);
        db.update("DELETE FROM spa_desk_staff WHERE user_id = ?", id);

        // Records the business must keep stay, with the free text and network details removed.
        db.update("UPDATE booking SET customer_notes = NULL, ip_address = NULL WHERE user_id = ?", id);
        db.update("UPDATE review SET meta = NULL WHERE user_id = ?", id);
        db.update("UPDATE chat_message SET content = '[Removed: the sender deleted their account]' WHERE sender_user_id = ?", id);
        db.update("UPDATE spa_listing_claim SET client_ip = NULL, user_agent = NULL WHERE user_id = ?", id);
        db.update("UPDATE spa_manager SET upi_id = NULL, upi_qr_image_url = NULL WHERE user_id = ?", id);
        db.update("UPDATE spa s JOIN spa_manager m ON m.id = s.spa_manager_id SET s.owner_name = NULL "
                + "WHERE m.user_id = ? AND s.is_active = 0", id);

        // The account row stays so kept records still join, but nothing on it identifies anyone.
        // Clearing email and phone frees them for a new account.
        db.update("UPDATE app_user SET email = NULL, phone = NULL, name = NULL, last_name = NULL, display_name = NULL, "
                        + "username = ?, password = ?, password_hash = NULL, password_salt = NULL, profile_image_url = NULL, "
                        + "meta = NULL, referral_code = NULL, referred_by = NULL, preferred_contact_method = NULL, "
                        + "whatsapp_updates_at = NULL, marketing_opt_in = 0, email_verified = 0, phone_verified = 0, "
                        + "mfa_enabled = 0, loyalty_points = 0, last_login_at = NULL, locale = NULL, timezone = 'UTC', "
                        + "privacy_consent_version = NULL, privacy_consented_at = NULL, consent_source = 'erased', "
                        + "active = 0, is_active = 0, deleted_at = ?, data_erased_at = ?, credentials_changed_at = ? WHERE id = ?",
                "deleted-" + id, passwordEncoder.encode(UUID.randomUUID().toString()), at, at, at, id);
        audit.record(id, AccountErasureLog.ERASED, null);
        return true;
    }

    /** Everything Nirvana holds about the signed-in person, in a form they can read and keep. */
    @Transactional(readOnly = true)
    public Map<String, Object> export(long userId) {
        User u = find(userId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", OffsetDateTime.now().toString());
        out.put("about", "Your personal data held by Nirvana, provided under the Digital Personal Data Protection Act, 2023. "
                + "Amounts are in the smallest currency unit (paise for INR).");
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("accountId", u.getId());
        profile.put("username", u.getUsername());
        profile.put("email", u.getEmail());
        profile.put("emailVerified", Boolean.TRUE.equals(u.getEmailVerified()));
        profile.put("phone", u.getPhone());
        profile.put("phoneVerified", Boolean.TRUE.equals(u.getPhoneVerified()));
        profile.put("firstName", u.getName());
        profile.put("lastName", u.getLastName());
        profile.put("displayName", u.getDisplayName());
        profile.put("profileImageUrl", u.getProfileImageUrl());
        profile.put("timezone", u.getTimezone());
        profile.put("locale", u.getLocale());
        profile.put("loyaltyPoints", u.getLoyaltyPoints());
        profile.put("referralCode", u.getReferralCode());
        profile.put("createdAt", text(u.getCreatedAt()));
        profile.put("lastSignInAt", text(u.getLastLoginAt()));
        out.put("profile", profile);

        Map<String, Object> consents = new LinkedHashMap<>();
        consents.put("privacyPolicyVersion", u.getPrivacyConsentVersion());
        consents.put("privacyPolicyAcceptedAt", text(u.getPrivacyConsentedAt()));
        consents.put("source", u.getConsentSource());
        consents.put("marketingOptIn", Boolean.TRUE.equals(u.getMarketingOptIn()));
        consents.put("whatsappUpdatesSince", text(u.getWhatsappUpdatesAt()));
        out.put("consents", consents);

        Map<String, Object> notifications = new LinkedHashMap<>();
        notifications.put("offersByEmail", Boolean.TRUE.equals(u.getMarketingOptIn()));
        notifications.put("bookingUpdatesOnWhatsapp", u.getWhatsappUpdatesAt() != null);
        notifications.put("preferredContactMethod", u.getPreferredContactMethod());
        out.put("notificationPreferences", notifications);

        out.put("signInMethods", rows("SELECT google_email AS googleEmail FROM customer_google_identity WHERE user_id = ?", userId));
        out.put("bookings", rows("SELECT b.booking_reference AS reference, s.name AS spa, sv.name AS service, b.start_ts AS startsAt, "
                + "b.end_ts AS endsAt, b.status, b.guest_count AS guests, b.currency, b.price_cents AS priceAmount, "
                + "b.discount_cents AS discountAmount, b.tax_cents AS taxAmount, b.tip_cents AS tipAmount, "
                + "b.loyalty_points_redeemed AS pointsUsed, b.loyalty_points_earned AS pointsEarned, b.customer_notes AS yourNotes, "
                + "b.therapist_preference AS therapistPreference, b.created_at AS bookedAt, b.cancelled_at AS cancelledAt, "
                + "b.cancellation_reason_text AS cancellationReason FROM booking b LEFT JOIN spa s ON s.id = b.spa_id "
                + "LEFT JOIN service sv ON sv.id = b.service_id WHERE b.user_id = ? ORDER BY b.start_ts DESC", userId));
        out.put("payments", rows("SELECT b.booking_reference AS booking, p.amount_cents AS amount, p.currency, "
                + "p.payment_status AS status, p.payment_method AS method, p.card_brand AS cardBrand, p.card_last4 AS cardLast4, "
                + "p.captured_at AS paidAt, p.refunded_cents AS refundedAmount, p.refunded_at AS refundedAt "
                + "FROM payment p JOIN booking b ON b.id = p.booking_id WHERE b.user_id = ? ORDER BY p.created_at DESC", userId));
        out.put("refunds", rows("SELECT b.booking_reference AS booking, r.amount_cents AS amount, r.currency, r.status, "
                + "r.reason, r.created_at AS requestedAt, r.processed_at AS processedAt FROM payment_refund r "
                + "JOIN booking b ON b.id = r.booking_id WHERE b.user_id = ? ORDER BY r.created_at DESC", userId));
        out.put("invoices", rows("SELECT i.invoice_number AS number, b.booking_reference AS booking, i.issued_at AS issuedAt, "
                + "i.total_cents AS total, i.tax_cents AS tax, i.currency, i.status FROM invoice i "
                + "LEFT JOIN booking b ON b.id = i.booking_id WHERE i.user_id = ? ORDER BY i.issued_at DESC", userId));
        out.put("spaPackages", rows("SELECT s.name AS spa, p.title AS package, x.level, x.price_paid AS pricePaid, x.currency, "
                + "x.total_sessions AS totalSessions, x.remaining_sessions AS remainingSessions, x.status, "
                + "x.payment_status AS paymentStatus, x.purchase_date AS purchasedAt, x.expiry_date AS expiresAt "
                + "FROM user_spa_package_subscription x LEFT JOIN spa s ON s.id = x.spa_id "
                + "LEFT JOIN spa_packages p ON p.id = x.spa_package_id WHERE x.user_id = ? ORDER BY x.purchase_date DESC", userId));
        out.put("packages", rows("SELECT package_id AS packageId, package_type AS type, amount_cents AS amount, currency, "
                + "purchased_sessions AS purchasedSessions, remaining_sessions AS remainingSessions, status, "
                + "payment_status AS paymentStatus, purchase_date AS purchasedAt, expiry_date AS expiresAt "
                + "FROM user_package_subscription WHERE user_id = ? ORDER BY purchase_date DESC", userId));
        out.put("vouchers", rows("SELECT code, amount_cents AS amount, currency, expires_at AS expiresAt, redeemed_at AS redeemedAt "
                + "FROM voucher WHERE user_id = ? ORDER BY created_at DESC", userId));
        out.put("reviews", rows("SELECT s.name AS spa, r.rating, r.title, r.text, r.created_at AS writtenAt, "
                + "r.reply_text AS spaReply FROM review r LEFT JOIN spa s ON s.id = r.spa_id WHERE r.user_id = ? "
                + "ORDER BY r.created_at DESC", userId));
        out.put("favouriteSpas", rows("SELECT s.name AS spa, f.created_at AS savedAt FROM user_favorite_spa f "
                + "JOIN spa s ON s.id = f.spa_id WHERE f.user_id = ?", userId));
        Status status = status(u, List.of());
        out.put("accountDeletion", status.pending()
                ? Map.of("requestedAt", text(status.requestedAt()), "eraseAfter", text(status.eraseAfter()))
                : Map.of());
        return out;
    }

    private List<Map<String, Object>> rows(String sql, Object... args) {
        return db.queryForList(sql, args).stream().map(row -> {
            Map<String, Object> clean = new LinkedHashMap<>();
            row.forEach((k, v) -> clean.put(k, plain(v)));
            return clean;
        }).toList();
    }

    /** Dates as ISO text, whatever type the driver returns, so the file reads the same everywhere. */
    private static Object plain(Object v) {
        if (v instanceof Timestamp t) return t.toLocalDateTime().toString();
        if (v instanceof java.time.temporal.TemporalAccessor || v instanceof java.util.Date) return v.toString();
        return v;
    }

    private static String text(Object time) {
        return time == null ? null : time.toString();
    }

    private User find(long userId) {
        User user = users.findById(userId).orElseThrow(() -> new EntityNotFoundException("Account not found"));
        if (user.getDataErasedAt() != null) throw new EntityNotFoundException("Account not found");
        return user;
    }
}
