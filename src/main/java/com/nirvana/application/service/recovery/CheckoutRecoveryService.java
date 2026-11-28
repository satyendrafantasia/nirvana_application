package com.nirvana.application.service.recovery;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.CheckoutRecovery;
import com.nirvana.application.model.Payment;
import com.nirvana.application.model.User;
import com.nirvana.application.model.dto.CheckoutReminderEmail;
import com.nirvana.application.model.dto.SlotAvailabilityResponse;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.CancellationActor;
import com.nirvana.application.model.enums.PaymentMode;
import com.nirvana.application.model.enums.PaymentStatus;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.CheckoutRecoveryRepository;
import com.nirvana.application.repository.PaymentRepository;
import com.nirvana.application.service.EmailService;
import com.nirvana.application.service.impl.BookingEmailContent;
import com.nirvana.application.service.impl.SessionAllocation;
import com.nirvana.application.service.impl.SlotAvailabilityService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Reminds guests who chose a time but never finished paying. One reminder per abandoned checkout,
 * at most one per guest in a rolling window, and never when the guest has since booked, cancelled
 * on purpose, opted out, or the appointment is too close. Clicks and later bookings at the same spa
 * are recorded so the recovered revenue can be reported.
 */
@Slf4j
@Service
public class CheckoutRecoveryService {

    /** Why a checkout was not reminded. Checked in this order. */
    public enum Skip {
        NOT_FOUND, NOT_ONLINE, NOT_ABANDONED, BOOKED_BY_STAFF, PAID, PAYMENT_LINK, STILL_PAYABLE,
        TOO_RECENT, TOO_OLD, SLOT_TOO_SOON, ALREADY_REMINDED, ACCOUNT_INACTIVE, OPTED_OUT, BOOKED_SINCE,
        RATE_LIMITED, NO_CONTACT
    }

    public record Claim(Long recoveryId, Long bookingId, CheckoutRecovery.Channel channel, String destination) {}

    /** Exactly one of {@code skipped} and {@code claim} is set. */
    public record Outcome(Skip skipped, Claim claim) {
        static Outcome skip(Skip reason) { return new Outcome(reason, null); }
    }

    public record AlternativeTime(Long slotId, OffsetDateTime startTs, LocalDate date) {}

    /** What the booking flow needs to pick up where the guest left off. */
    public record Resume(Long spaId, String spaName, Long serviceId, String serviceName, boolean serviceAvailable,
                         int guests, String therapistId, String therapistType, String therapistName,
                         LocalDate date, OffsetDateTime originalStart, Long originalSlotId, boolean originalAvailable,
                         String pendingBookingReference, List<AlternativeTime> alternatives) {}

    public record Stats(OffsetDateTime from, OffsetDateTime to, long sent, long failed, long clicked, long converted,
                        long recoveredCents, double clickRate, double conversionRate) {}

    private static final List<PaymentStatus> PAID = List.of(PaymentStatus.AUTHORIZED, PaymentStatus.CAPTURED,
            PaymentStatus.COMPLETED, PaymentStatus.REFUNDED, PaymentStatus.PARTIALLY_REFUNDED);
    private static final int ALTERNATIVES = 6;
    private static final int ALTERNATIVE_DAYS = 7;

    private final CheckoutRecoveryRepository recoveries;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final EmailService email;
    private final CheckoutRecoveryWhatsApp whatsApp;
    private final CheckoutRecoveryTokens tokens;
    private final SlotAvailabilityService availability;
    private final EntityManager em;
    private final Settings settings;

    /** Tunables, all under checkout-recovery.* in application.yaml. */
    public record Settings(boolean enabled, int delayMinutes, int maxAgeMinutes, int minLeadMinutes, int perUserHours,
                           int linkTtlHours, int unsubscribeTtlDays, int attributionDays, int pendingExpiryMinutes,
                           String frontendUrl, String apiBaseUrl) {
        String api() {
            return apiBaseUrl == null || apiBaseUrl.isBlank() ? frontendUrl : apiBaseUrl;
        }
    }

    public CheckoutRecoveryService(CheckoutRecoveryRepository recoveries, BookingRepository bookings,
                                   PaymentRepository payments, EmailService email, CheckoutRecoveryWhatsApp whatsApp,
                                   CheckoutRecoveryTokens tokens, SlotAvailabilityService availability, EntityManager em,
                                   @Value("${checkout-recovery.enabled:true}") boolean enabled,
                                   @Value("${checkout-recovery.delay-minutes:40}") int delayMinutes,
                                   @Value("${checkout-recovery.max-age-minutes:360}") int maxAgeMinutes,
                                   @Value("${checkout-recovery.min-lead-minutes:120}") int minLeadMinutes,
                                   @Value("${checkout-recovery.per-user-hours:24}") int perUserHours,
                                   @Value("${checkout-recovery.link-ttl-hours:48}") int linkTtlHours,
                                   @Value("${checkout-recovery.unsubscribe-ttl-days:365}") int unsubscribeTtlDays,
                                   @Value("${checkout-recovery.attribution-days:7}") int attributionDays,
                                   @Value("${reliability.bookings.pending-payment-expiry-minutes:30}") int pendingExpiryMinutes,
                                   @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
                                   @Value("${checkout-recovery.api-base-url:}") String apiBaseUrl) {
        this.recoveries = recoveries;
        this.bookings = bookings;
        this.payments = payments;
        this.email = email;
        this.whatsApp = whatsApp;
        this.tokens = tokens;
        this.availability = availability;
        this.em = em;
        this.settings = new Settings(enabled, delayMinutes, maxAgeMinutes, minLeadMinutes, perUserHours, linkTtlHours,
                unsubscribeTtlDays, attributionDays, pendingExpiryMinutes, stripSlash(frontendUrl), stripSlash(apiBaseUrl));
    }

    public Settings settings() {
        return settings;
    }

    public boolean active() {
        return settings.enabled() && tokens.configured();
    }

    // ------------------ FINDING AND CLAIMING ------------------

    @Transactional(readOnly = true)
    public List<Long> candidates(OffsetDateTime now, int limit) {
        return recoveries.findAbandonedCheckoutIds(now.minusMinutes(settings.delayMinutes()),
                now.minusMinutes(settings.maxAgeMinutes()), now.plusMinutes(settings.minLeadMinutes()),
                PageRequest.of(0, Math.max(1, limit)));
    }

    /**
     * Checks every rule under a lock on the booking and the guest, and records the reminder before
     * it is sent, so a second instance or a restart can never send another for this checkout.
     */
    @Transactional
    public Outcome claim(Long bookingId, OffsetDateTime now) {
        Booking b = bookings.findByIdForUpdate(bookingId).orElse(null);
        Skip skip = eligibility(b, now);
        if (skip != null) return Outcome.skip(skip);

        User user = b.getUser();
        if (!active(user)) return Outcome.skip(Skip.ACCOUNT_INACTIVE);
        // Serialises reminders for one guest across bookings and instances.
        em.createNativeQuery("SELECT id FROM app_user WHERE id = ? FOR UPDATE").setParameter(1, user.getId()).getResultList();
        if (optedOut(user.getId())) return Outcome.skip(Skip.OPTED_OUT);
        if (recoveries.countBookedSince(user.getId(), b.getId(), b.getCreatedAt()) > 0) return Outcome.skip(Skip.BOOKED_SINCE);
        if (recoveries.countByUserIdAndSentAtAfter(user.getId(), now.minusHours(settings.perUserHours())) > 0)
            return Outcome.skip(Skip.RATE_LIMITED);

        CheckoutRecovery.Channel channel;
        String destination;
        Optional<String> phone = whatsApp.recipient(b);
        if (phone.isPresent()) {
            channel = CheckoutRecovery.Channel.WHATSAPP;
            destination = phone.get();
        } else if (user.getEmail() != null && !user.getEmail().isBlank()) {
            channel = CheckoutRecovery.Channel.EMAIL;
            destination = user.getEmail().strip();
        } else {
            return Outcome.skip(Skip.NO_CONTACT);
        }

        CheckoutRecovery row = new CheckoutRecovery();
        row.setBookingId(b.getId());
        row.setUserId(user.getId());
        row.setSpaId(b.getSpa().getId());
        row.setChannel(channel);
        row.setStatus(CheckoutRecovery.Status.PENDING);
        row.setTokenNonce(CheckoutRecoveryTokens.newNonce());
        row.setTokenExpiresAt(now.plusHours(settings.linkTtlHours()));
        row.setSentAt(now);
        recoveries.saveAndFlush(row);
        return new Outcome(null, new Claim(row.getId(), b.getId(), channel, destination));
    }

    /** The booking-level rules; null when the checkout may be reminded. */
    Skip eligibility(Booking b, OffsetDateTime now) {
        if (b == null) return Skip.NOT_FOUND;
        if (b.getPaymentMode() != PaymentMode.ONLINE) return Skip.NOT_ONLINE;
        boolean pending = b.getStatus() == BookingStatus.PENDING_PAYMENT;
        boolean timedOut = b.getStatus() == BookingStatus.CANCELLED && b.getCancelledBy() == CancellationActor.SYSTEM
                && "PAYMENT_TIMEOUT".equals(b.getCancellationReasonCode());
        // Confirmed, completed, or cancelled on purpose by the guest or the spa.
        if (!pending && !timedOut) return Skip.NOT_ABANDONED;
        if (b.getScheduledBy() == CancellationActor.SPA || b.getScheduledBy() == CancellationActor.ADMIN)
            return Skip.BOOKED_BY_STAFF;
        List<Payment> paid = payments.findByBookingId(b.getId());
        if (paid.stream().anyMatch(p -> PAID.contains(p.getPaymentStatus()))) return Skip.PAID;
        // The spa sent this guest a payment link; it stays payable on its own schedule.
        if (paid.stream().anyMatch(p -> p.getPaymentLinkId() != null)) return Skip.PAYMENT_LINK;
        if (pending && b.getCreatedAt().plusMinutes(settings.pendingExpiryMinutes()).isAfter(now)) return Skip.STILL_PAYABLE;
        if (b.getCreatedAt().isAfter(now.minusMinutes(settings.delayMinutes()))) return Skip.TOO_RECENT;
        if (b.getCreatedAt().isBefore(now.minusMinutes(settings.maxAgeMinutes()))) return Skip.TOO_OLD;
        if (!b.getStartTs().isAfter(now.plusMinutes(settings.minLeadMinutes()))) return Skip.SLOT_TOO_SOON;
        if (recoveries.existsByBookingId(b.getId())) return Skip.ALREADY_REMINDED;
        return null;
    }

    private static boolean active(User u) {
        return u != null && u.isActive() && !Boolean.FALSE.equals(u.getIsActive()) && u.getDeletedAt() == null
                && u.getDataErasedAt() == null && u.getDataErasureRequestedAt() == null;
    }

    // ------------------ SENDING ------------------

    /** Sends the claimed reminder once. A failure is recorded and not retried. */
    @Transactional
    public boolean deliver(Claim claim) {
        CheckoutRecovery row = recoveries.findById(claim.recoveryId()).orElseThrow();
        if (row.getStatus() != CheckoutRecovery.Status.PENDING) return false;
        Booking b = bookings.findById(claim.bookingId()).orElseThrow();
        String resumeToken = tokens.sign(row.getId(), row.getTokenNonce(), CheckoutRecoveryTokens.Purpose.RESUME,
                row.getTokenExpiresAt().toInstant());
        boolean sent;
        try {
            if (claim.channel() == CheckoutRecovery.Channel.WHATSAPP) {
                whatsApp.send(claim.destination(), b, resumeToken);
                sent = true;
            } else {
                String unsubscribe = tokens.sign(row.getId(), row.getTokenNonce(), CheckoutRecoveryTokens.Purpose.UNSUBSCRIBE,
                        row.getSentAt().toInstant().plus(Duration.ofDays(settings.unsubscribeTtlDays())));
                sent = email.sendCheckoutReminder(new CheckoutReminderEmail(claim.destination(), b.getUser().getName(),
                        b.getSpa().getName(), b.getService().getName(), BookingEmailContent.formatStart(b.getStartTs(), b.getSpa()),
                        resumeUrl(resumeToken), settings.frontendUrl() + "/booking/stop-reminders?t=" + unsubscribe,
                        settings.api() + "/api/public/checkout-reminders/unsubscribe?t=" + unsubscribe,
                        b.getBookingReference()));
            }
        } catch (RuntimeException ex) {
            log.warn("Checkout reminder {} could not be sent: {}", row.getId(), ex.getClass().getSimpleName());
            sent = false;
        }
        row.setStatus(sent ? CheckoutRecovery.Status.SENT : CheckoutRecovery.Status.FAILED);
        recoveries.save(row);
        return sent;
    }

    public String resumeUrl(String token) {
        return settings.frontendUrl() + "/booking/resume/" + token;
    }

    // ------------------ CONVERSIONS ------------------

    /**
     * Marks reminders whose guest went on to book at the same spa within the attribution window,
     * with that booking's total as recovered revenue. Returns how many were marked.
     */
    @Transactional
    public int recordConversions(OffsetDateTime now) {
        int marked = 0;
        for (CheckoutRecovery row : recoveries.findByConvertedAtIsNullAndStatusAndSentAtAfter(
                CheckoutRecovery.Status.SENT, now.minusDays(settings.attributionDays()))) {
            List<Booking> found = recoveries.findConversions(row.getUserId(), row.getSpaId(), row.getSentAt(),
                    row.getBookingId(), PageRequest.of(0, 1));
            if (found.isEmpty()) continue;
            Booking b = found.get(0);
            row.setConvertedAt(now);
            row.setConvertedBookingId(b.getId());
            row.setRecoveredCents(total(b));
            recoveries.save(row);
            marked++;
        }
        return marked;
    }

    static int total(Booking b) {
        return Math.max(0, nz(b.getPriceCents()) + nz(b.getTaxCents()) - nz(b.getDiscountCents()));
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    // ------------------ RESUMING ------------------

    /** Records the click and returns what to prefill. Unknown, expired or someone else's links are not found. */
    @Transactional
    public Resume resume(String token, Long userId, OffsetDateTime now) {
        CheckoutRecovery row = row(token, CheckoutRecoveryTokens.Purpose.RESUME, now);
        if (!row.getUserId().equals(userId)) throw gone();
        if (row.getClickedAt() == null) row.setClickedAt(now);
        row.setClickCount(row.getClickCount() + 1);
        recoveries.save(row);

        Booking b = bookings.findById(row.getBookingId()).orElseThrow(CheckoutRecoveryService::gone);
        var spa = b.getSpa();
        var service = b.getService();
        boolean serviceAvailable = Boolean.TRUE.equals(spa.getIsActive()) && Boolean.TRUE.equals(service.getIsActive());
        ZoneId zone = zone(spa.getTimezone());
        LocalDate date = b.getStartTs().atZoneSameInstant(zone).toLocalDate();
        LocalDate today = now.atZoneSameInstant(zone).toLocalDate();
        if (date.isBefore(today)) date = today;

        String mode = b.getTherapistPreference();
        String therapistId = null;
        String therapistType = null;
        if ((mode == null || "NAMED".equals(mode)) && b.getTherapist() != null
                && Boolean.TRUE.equals(spa.getAllowTherapistSelection()) && Boolean.TRUE.equals(b.getTherapist().getIsActive())) {
            therapistId = String.valueOf(b.getTherapist().getId());
        } else if ("LABEL".equals(mode) && b.getTherapistType() != null) {
            therapistType = b.getTherapistType();
        }
        String therapistName = therapistId == null && therapistType == null ? null
                : SessionAllocation.customerDisplay(spa, b.getTherapist());
        if (therapistType != null && therapistName == null) therapistName = therapistType;

        int guests = b.getGuestCount() == null ? 1 : b.getGuestCount();
        String pendingReference = b.getStatus() == BookingStatus.PENDING_PAYMENT ? b.getBookingReference() : null;
        Long originalSlot = null;
        List<AlternativeTime> alternatives = List.of();
        if (serviceAvailable) {
            List<AlternativeTime> free = freeTimes(spa.getId(), service.getId(), date, guests, now);
            OffsetDateTime original = b.getStartTs();
            // While the guest's own unpaid booking still holds the time, it can't be offered again.
            Optional<AlternativeTime> same = pendingReference != null ? Optional.empty()
                    : free.stream().filter(t -> t.startTs().toInstant().equals(original.toInstant())).findFirst();
            if (same.isPresent()) {
                originalSlot = same.get().slotId();
            } else {
                alternatives = free.stream()
                        .sorted(Comparator.comparingLong(t -> Math.abs(Duration.between(original, t.startTs()).toMinutes())))
                        .limit(ALTERNATIVES)
                        .sorted(Comparator.comparing(AlternativeTime::startTs))
                        .toList();
            }
        }
        boolean originalAvailable = originalSlot != null;
        return new Resume(spa.getId(), spa.getName(), service.getId(), service.getName(), serviceAvailable, guests,
                therapistId, therapistType, therapistName, date, b.getStartTs(), originalSlot, originalAvailable,
                pendingReference, alternatives);
    }

    private List<AlternativeTime> freeTimes(Long spaId, Long serviceId, LocalDate from, int guests, OffsetDateTime now) {
        OffsetDateTime earliest = now.plusMinutes(30);
        return availability.getAvailableSlotsForServiceRange(spaId, serviceId, from, ALTERNATIVE_DAYS, guests).days().stream()
                .flatMap(day -> day.slots().stream().map(s -> new AlternativeTime(s.slotId(), s.startTs(), day.date()) )
                        .filter(t -> t.startTs() != null && t.startTs().isAfter(earliest))
                        .filter(t -> bookable(day.slots(), t.slotId(), guests)))
                .toList();
    }

    private static boolean bookable(List<SlotAvailabilityResponse> slots, Long id, int guests) {
        return slots.stream().anyMatch(s -> s.slotId() != null && s.slotId().equals(id)
                && "AVAILABLE".equals(s.status()) && s.remainingCapacityUnits() >= guests);
    }

    // ------------------ OPTING OUT ------------------

    /** Turns reminders off for the guest the link was sent to. Returns false for an unknown link. */
    @Transactional
    public boolean unsubscribe(String token, OffsetDateTime now) {
        CheckoutRecovery row = row(token, CheckoutRecoveryTokens.Purpose.UNSUBSCRIBE, now);
        setOptedOut(row.getUserId(), true);
        return true;
    }

    /** Undo for the unsubscribe page. */
    @Transactional
    public void resubscribe(String token, OffsetDateTime now) {
        CheckoutRecovery row = row(token, CheckoutRecoveryTokens.Purpose.UNSUBSCRIBE, now);
        setOptedOut(row.getUserId(), false);
    }

    @Transactional
    public void setOptedOut(Long userId, boolean optedOut) {
        em.createNativeQuery(optedOut
                        ? "UPDATE app_user SET checkout_reminders_opted_out_at = COALESCE(checkout_reminders_opted_out_at, UTC_TIMESTAMP(6)) WHERE id = ?"
                        : "UPDATE app_user SET checkout_reminders_opted_out_at = NULL WHERE id = ?")
                .setParameter(1, userId).executeUpdate();
    }

    @Transactional(readOnly = true)
    public boolean optedOut(Long userId) {
        List<?> rows = em.createNativeQuery("SELECT checkout_reminders_opted_out_at FROM app_user WHERE id = ?")
                .setParameter(1, userId).getResultList();
        return !rows.isEmpty() && rows.get(0) != null;
    }

    private CheckoutRecovery row(String token, CheckoutRecoveryTokens.Purpose purpose, OffsetDateTime now) {
        var claims = tokens.verify(token, purpose, now.toInstant()).orElseThrow(CheckoutRecoveryService::gone);
        return recoveries.findByIdAndTokenNonce(claims.recoveryId(), claims.nonce()).orElseThrow(CheckoutRecoveryService::gone);
    }

    private static EntityNotFoundException gone() {
        return new EntityNotFoundException("This link has expired or is no longer valid");
    }

    // ------------------ STATS ------------------

    @Transactional(readOnly = true)
    public Stats stats(OffsetDateTime from, OffsetDateTime to, Long spaId) {
        var t = recoveries.totals(from, to, spaId);
        long sent = t == null ? 0 : n(t.getSent());
        long clicked = t == null ? 0 : n(t.getClicked());
        long converted = t == null ? 0 : n(t.getConverted());
        long recovered = t == null ? 0 : n(t.getRecoveredCents());
        return new Stats(from, to, sent, t == null ? 0 : n(t.getFailed()), clicked, converted, recovered,
                sent == 0 ? 0 : (double) clicked / sent, sent == 0 ? 0 : (double) converted / sent);
    }

    // ------------------ HELPERS ------------------

    private static long n(Long v) {
        return v == null ? 0 : v;
    }

    private static ZoneId zone(String timezone) {
        try {
            return timezone == null || timezone.isBlank() ? ZoneId.of("Asia/Kolkata") : ZoneId.of(timezone);
        } catch (RuntimeException invalid) {
            return ZoneId.of("Asia/Kolkata");
        }
    }

    private static String stripSlash(String url) {
        if (url == null) return null;
        String s = url.strip();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    static OffsetDateTime utcNow() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
