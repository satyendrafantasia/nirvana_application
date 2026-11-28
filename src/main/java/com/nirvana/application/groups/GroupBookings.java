package com.nirvana.application.groups;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.model.Booking;
import com.nirvana.application.model.BookingHold;
import com.nirvana.application.model.Slot;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.Therapist;
import com.nirvana.application.model.User;
import com.nirvana.application.model.dto.CancellationQuote;
import com.nirvana.application.model.dto.PaymentInitResponse;
import com.nirvana.application.model.enums.BookingPaymentType;
import com.nirvana.application.model.enums.BookingSource;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.CancellationActor;
import com.nirvana.application.model.enums.PaymentMode;
import com.nirvana.application.model.enums.RefundStatus;
import com.nirvana.application.model.enums.SlotStatus;
import com.nirvana.application.repository.BookingHoldRepository;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.SlotRepository;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.repository.TherapistRepository;
import com.nirvana.application.repository.UserRepository;
import com.nirvana.application.rewards.CheckoutMath;
import com.nirvana.application.security.PaymentMfaVerifier;
import com.nirvana.application.service.BookingService;
import com.nirvana.application.service.NotificationSchedulingService;
import com.nirvana.application.service.PricingService;
import com.nirvana.application.service.impl.LoyaltyService;
import com.nirvana.application.service.impl.SessionAllocation;
import com.nirvana.application.service.impl.TherapistRoster;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Couples and group bookings. Every guest gets an ordinary booking row, so the desk, receipts, reviews,
 * payouts, GST invoices and health forms keep working per guest. Holds and bookings for a group are
 * made in one transaction under the spa scheduling lock: all guests are seated or none are.
 *
 * <p>Pricing is evaluated once for the whole order (see docs/group-bookings.md): the coupon, off-peak
 * discount and loyalty points are priced against the group subtotal and split across the guests in
 * proportion to their treatment price; the coupon and the points are recorded on the booker's booking.
 * A gift card is quoted once against the group total and its share is redeemed per booking.
 * Wallet credit and packages are not offered for groups.
 */
@Service
@RequiredArgsConstructor
public class GroupBookings {
    private static final int HOLD_MINUTES = 10;
    private static final int DEFAULT_CUTOFF_MINUTES = 15;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final SpaRepository spas;
    private final com.nirvana.application.repository.ServiceRepository services;
    private final SlotRepository slots;
    private final UserRepository users;
    private final BookingRepository bookings;
    private final BookingHoldRepository holds;
    private final TherapistRepository therapists;
    private final GroupAvailability availability;
    private final PricingService pricing;
    private final LoyaltyService loyalty;
    private final NotificationSchedulingService notifications;
    private final GroupCheckout checkout;
    private final BookingService bookingService;
    private final PaymentMfaVerifier paymentMfa;

    public record GuestInput(String name, String phone, Long serviceId, Long therapistId, String therapistType) {}

    public record HoldRequest(Long spaId, OffsetDateTime startTs, List<GuestInput> guests) {}

    public record HoldReply(String holdToken, OffsetDateTime expiresAt, List<String> therapists) {}

    public record CreateRequest(Long spaId, OffsetDateTime startTs, String holdToken, String paymentMode, String couponCode,
                                Boolean redeemLoyaltyPoints, String giftCardCode, String specialRequest, String requestKey,
                                List<GuestInput> guests) {}

    public record MemberView(long bookingId, String bookingReference, int position, String guestName, boolean booker,
                             long serviceId, String serviceName, String therapistName, OffsetDateTime startTs,
                             OffsetDateTime endTs, String status, int priceCents, int discountCents, int taxCents,
                             int giftCardCents, int payableCents) {}

    public record GroupView(long groupId, long spaId, String spaName, int size, OffsetDateTime startTs, String paymentMode,
                            String currency, int totalCents, String paymentStatus, List<MemberView> members,
                            PaymentInitResponse razorpay) {}

    public record GroupQuote(long groupId, String currency, int refundCents, int feeCents, List<MemberQuote> members) {}

    public record MemberQuote(long bookingId, String guestName, CancellationQuote quote) {}

    static List<GroupAvailability.GuestRequest> requests(List<GuestInput> guests) {
        if (guests == null) throw new IllegalArgumentException("Add the guests in your group");
        return guests.stream().map(g -> new GroupAvailability.GuestRequest(g.serviceId(), g.therapistId(),
                StringUtils.hasText(g.therapistType()) ? g.therapistType().trim() : null)).toList();
    }

    // ---------------- holds ----------------

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HoldReply hold(Long userId, HoldRequest request) {
        Spa spa = spas.lockSchedule(request.spaId()).orElseThrow(() -> new EntityNotFoundException("Spa not found"));
        if (!"INSTANT".equals(spa.getBookingMode())) throw new IllegalStateException("Contact the spa or request acceptance before booking");
        if (request.startTs() == null) throw new IllegalArgumentException("Choose a time");
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        holds.deleteByExpiresAtBefore(now);
        var guests = requests(request.guests());
        var plan = availability.plan(spa, request.startTs(), guests, null, null);
        User user = users.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));
        String token = UUID.randomUUID().toString().replace("-", "");
        var expires = now.plusMinutes(HOLD_MINUTES);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < guests.size(); i++) {
            var guest = guests.get(i);
            Slot slot = plan.slot(i);
            Therapist therapist = therapists.findById(plan.seats().get(i).therapistId()).orElseThrow();
            BookingHold hold = new BookingHold();
            hold.setTherapistPreference(SessionAllocation.preference(guest.therapistId(), guest.therapistType()));
            hold.setRequestedLabel(guest.therapistType());
            hold.setRoom(slot.getRoom());
            hold.setTherapist(therapist);
            hold.setSpa(spa);
            hold.setServicesJson(guest.serviceId().toString());
            hold.setSlot(slot);
            hold.setUser(user);
            hold.setHoldToken(UUID.randomUUID().toString());
            hold.setExpiresAt(expires);
            hold.setHoldUnits(1);
            holds.saveAndFlush(hold);
            jdbc.update("INSERT INTO booking_group_hold(hold_id, group_token, position, service_id) VALUES (?,?,?,?)",
                    hold.getId(), token, i + 1, guest.serviceId());
            names.add(SessionAllocation.customerDisplay(spa, therapist));
        }
        return new HoldReply(token, expires, names);
    }

    // ---------------- checkout ----------------

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GroupView create(Long userId, CreateRequest request) {
        if (request == null || request.spaId() == null || request.startTs() == null) throw new IllegalArgumentException("Choose a spa and a time");
        Spa spa = spas.lockSchedule(request.spaId()).orElseThrow(() -> new EntityNotFoundException("Spa not found"));
        String key = StringUtils.hasText(request.requestKey()) ? request.requestKey().trim() : null;
        String fingerprint = null;
        if (key != null) {
            var fields = (com.fasterxml.jackson.databind.node.ObjectNode) json.valueToTree(request);
            fields.remove("holdToken");
            fingerprint = org.apache.commons.codec.digest.DigestUtils.sha256Hex(fields.toString());
            var earlier = jdbc.queryForList("SELECT id, fingerprint FROM booking_group WHERE booker_user_id=? AND request_key=?", userId, key);
            if (!earlier.isEmpty()) {
                if (!fingerprint.equals(earlier.get(0).get("fingerprint"))) throw new IllegalStateException("Checkout key already used for different booking details");
                return view(((Number) earlier.get(0).get("id")).longValue(), userId);
            }
        }
        if (!"INSTANT".equals(spa.getBookingMode())) throw new IllegalStateException("Contact the spa or request acceptance before booking");
        var guests = requests(request.guests());
        var names = guestNames(request.guests());
        User user = users.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));
        if (names.get(0) == null) names.set(0, StringUtils.hasText(user.getName()) ? user.getName() : "Booker");
        PaymentMode mode = PaymentMode.valueOf(Optional.ofNullable(request.paymentMode()).orElse("OFFLINE").trim().toUpperCase());

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        holds.deleteByExpiresAtBefore(now);
        List<Long> fixedSlots = null, fixedStaff = null;
        if (StringUtils.hasText(request.holdToken())) {
            var held = jdbc.queryForList("""
                    SELECT h.id, g.position, g.service_id, h.slot_id, h.therapist_id, h.user_id, h.converted_to_booking
                    FROM booking_group_hold g JOIN booking_hold h ON h.id=g.hold_id WHERE g.group_token=? ORDER BY g.position FOR UPDATE
                    """, request.holdToken().trim());
            if (held.size() != guests.size()) throw new IllegalStateException(SessionAllocation.TAKEN + "; your hold expired");
            fixedSlots = new ArrayList<>();
            fixedStaff = new ArrayList<>();
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < held.size(); i++) {
                var row = held.get(i);
                if (!Objects.equals(((Number) row.get("user_id")).longValue(), userId)) throw new IllegalArgumentException("Hold belongs to a different user");
                if (Boolean.TRUE.equals(row.get("converted_to_booking")) || Objects.equals(row.get("converted_to_booking"), 1))
                    throw new IllegalStateException("Hold already used for booking");
                if (((Number) row.get("service_id")).longValue() != guests.get(i).serviceId())
                    throw new IllegalStateException("Your group changed; reserve the time again");
                fixedSlots.add(((Number) row.get("slot_id")).longValue());
                fixedStaff.add(((Number) row.get("therapist_id")).longValue());
                ids.add(((Number) row.get("id")).longValue());
            }
            // Released before planning, so the group does not compete with its own reservation.
            for (Long id : ids) jdbc.update("UPDATE booking_hold SET converted_to_booking=1 WHERE id=?", id);
        }
        var plan = availability.plan(spa, request.startTs(), guests, fixedSlots, fixedStaff);
        int size = guests.size();

        // Price the order once, then split it per guest.
        List<com.nirvana.application.model.Service> treatments = new ArrayList<>();
        int[] prices = new int[size];
        for (int i = 0; i < size; i++) {
            var service = services.findById(guests.get(i).serviceId()).orElseThrow();
            treatments.add(service);
            prices[i] = service.getPriceCents() != null ? service.getPriceCents()
                    : service.getBasePriceCents() != null ? service.getBasePriceCents() : 0;
        }
        String currency = Optional.ofNullable(treatments.get(0).getCurrency()).orElse(spa.getDefaultCurrency());
        for (var service : treatments)
            if (!Objects.equals(Optional.ofNullable(service.getCurrency()).orElse(spa.getDefaultCurrency()), currency))
                throw new IllegalArgumentException("Guests' treatments must be priced in one currency");
        int subtotal = Arrays.stream(prices).sum();
        var priced = pricing.evaluatePricing(user, spa, treatments.get(0), plan.slot(0), size, subtotal,
                request.couponCode(), Boolean.TRUE.equals(request.redeemLoyaltyPoints()));
        int[] discounts = GroupPlanner.proRata(priced.discountCents(), prices);
        int[] totals = new int[size];
        int[] taxes = new int[size];
        for (int i = 0; i < size; i++) {
            taxes[i] = CheckoutMath.bookingTax(spa, treatments.get(i), prices[i] - discounts[i]);
            totals[i] = prices[i] - discounts[i] + taxes[i];
        }
        int groupTotal = Arrays.stream(totals).sum();
        String giftCard = StringUtils.hasText(request.giftCardCode()) ? request.giftCardCode().trim() : null;
        int giftCardCents = giftCard == null ? 0 : loyalty.giftCardShare(giftCard, spa, groupTotal);
        int[] giftShares = GroupPlanner.proRata(giftCardCents, totals);
        int payable = groupTotal - giftCardCents;
        BookingStatus status = mode == PaymentMode.OFFLINE || payable == 0 ? BookingStatus.CONFIRMED : BookingStatus.PENDING_PAYMENT;

        long groupId = insertGroup(spa, userId, size, request.startTs(), currency, payable, mode, key, fingerprint);
        List<Booking> created = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Slot slot = plan.slot(i);
            var seat = plan.seats().get(i);
            var guest = guests.get(i);
            var service = treatments.get(i);
            Therapist therapist = therapists.findById(seat.therapistId()).orElseThrow();
            Booking booking = new Booking();
            booking.setSpa(spa);
            booking.setService(service);
            booking.setSlot(slot);
            booking.setUser(user);
            booking.setBookingReference(reference());
            booking.setCurrency(currency);
            booking.setGuestCount(1);
            booking.setPriceCents(prices[i]);
            booking.setDiscountCents(discounts[i]);
            if (i == 0) {
                booking.setCouponCode(priced.appliedCouponCode());
                booking.setCoupon(priced.coupon());
                booking.setLoyaltyPointsRedeemed(priced.loyaltyPointsRedeemed());
                booking.setCustomerNotes(request.specialRequest());
            } else {
                booking.setLoyaltyPointsRedeemed(0);
            }
            booking.setGiftCardCents(giftShares[i]);
            booking.setTaxCents(taxes[i]);
            booking.setDepositCents(0);
            booking.setRemainderCents(totals[i] - giftShares[i]);
            booking.setRefundStatus(RefundStatus.NONE);
            booking.setPolicySnapshotJson(service.getCancellationPolicyJson() != null ? service.getCancellationPolicyJson()
                    : "{\"defaultCutoffMinutes\":" + DEFAULT_CUTOFF_MINUTES + ",\"defaultFeePercent\":0,\"defaultRoute\":\"ORIGINAL_METHOD\"}");
            booking.setStartTs(slot.getStartTs());
            booking.setEndTs(TherapistRoster.end(slot));
            booking.setScheduledBy(CancellationActor.USER);
            booking.setLastStatusChangedAt(now);
            booking.setPaymentMode(mode);
            booking.setPaymentType(BookingPaymentType.STANDARD);
            booking.setRoom(slot.getRoom());
            booking.setTherapistPreference(SessionAllocation.preference(guest.therapistId(), guest.therapistType()));
            booking.setTherapist(therapist);
            booking.setTherapistType(therapist.getType());
            booking.setBookingSource(BookingSource.WEB);
            booking.setStatus(status);
            Booking saved = bookings.saveAndFlush(booking);
            created.add(saved);

            // A shared couples-room slot can carry more guests than its unit count: the planner checked the room.
            short units = (short) (slot.getBookedUnits() + 1);
            slot.setBookedUnits(units);
            if (units >= slot.getCapacityUnit()) slot.setStatus(SlotStatus.BOOKED);
            slots.save(slot);

            jdbc.update("INSERT INTO booking_group_member(booking_id, group_id, position, guest_name, guest_phone, is_booker) VALUES (?,?,?,?,?,?)",
                    saved.getId(), groupId, i + 1, names.get(i), phone(request.guests().get(i).phone()), i == 0);
            int points = i == 0 ? priced.loyaltyPointsRedeemed() : 0;
            if (points > 0 || giftShares[i] > 0) loyalty.redeemAtCheckout(saved, points, giftCard, giftShares[i]);
        }

        PaymentInitResponse razorpay = null;
        if (status == BookingStatus.CONFIRMED) {
            created.forEach(notifications::scheduleBookingNotifications);
        } else {
            paymentMfa.verifyPaymentChallenge();
            razorpay = checkout.initiate(groupId);
        }
        var view = view(groupId, userId);
        return new GroupView(view.groupId(), view.spaId(), view.spaName(), view.size(), view.startTs(), view.paymentMode(),
                view.currency(), view.totalCents(), view.paymentStatus(), view.members(), razorpay);
    }

    private long insertGroup(Spa spa, Long userId, int size, OffsetDateTime start, String currency, int total, PaymentMode mode,
                             String key, String fingerprint) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement s = c.prepareStatement("""
                    INSERT INTO booking_group(spa_id, booker_user_id, group_size, start_ts, currency, total_cents, payment_mode,
                      payment_status, request_key, fingerprint, created_at, updated_at)
                    VALUES (?,?,?,?,?,?,?,'NONE',?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                    """, Statement.RETURN_GENERATED_KEYS);
            s.setLong(1, spa.getId());
            s.setLong(2, userId);
            s.setInt(3, size);
            s.setTimestamp(4, Timestamp.valueOf(start.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()));
            s.setString(5, currency);
            s.setInt(6, total);
            s.setString(7, mode.name());
            s.setString(8, key);
            s.setString(9, fingerprint);
            return s;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    private static List<String> guestNames(List<GuestInput> guests) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < guests.size(); i++) {
            String name = guests.get(i).name() == null ? null : guests.get(i).name().trim().replaceAll("\\s+", " ");
            if (!StringUtils.hasText(name)) {
                if (i > 0) throw new IllegalArgumentException("Enter a name for guest " + (i + 1));
                name = null;
            } else if (name.length() > 120) {
                throw new IllegalArgumentException("Guest names can be at most 120 characters");
            }
            names.add(name);
        }
        return names;
    }

    private static String phone(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String phone = raw.replaceAll("[\\s-]", "");
        if (!phone.matches("\\+?[0-9]{7,15}")) throw new IllegalArgumentException("Enter a valid phone number or leave it blank");
        return phone;
    }

    private String reference() {
        while (true) {
            String ref = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
            if (!bookings.existsByBookingReference(ref)) return ref;
        }
    }

    // ---------------- reading ----------------

    @Transactional(readOnly = true)
    public GroupView view(long groupId, Long userId) {
        var rows = jdbc.queryForList("SELECT * FROM booking_group WHERE id=?", groupId);
        if (rows.isEmpty()) throw new EntityNotFoundException("Group booking not found");
        var group = rows.get(0);
        if (!Objects.equals(((Number) group.get("booker_user_id")).longValue(), userId)) throw new AccessDeniedException("Booking access denied");
        return toView(group);
    }

    @Transactional(readOnly = true)
    public List<GroupView> mine(Long userId) {
        return jdbc.queryForList("SELECT * FROM booking_group WHERE booker_user_id=? ORDER BY start_ts DESC LIMIT 100", userId)
                .stream().map(this::toView).toList();
    }

    private GroupView toView(Map<String, Object> group) {
        long groupId = ((Number) group.get("id")).longValue();
        Spa spa = spas.findById(((Number) group.get("spa_id")).longValue()).orElseThrow();
        List<MemberView> members = new ArrayList<>();
        for (var member : jdbc.queryForList("SELECT booking_id, position, guest_name, is_booker FROM booking_group_member WHERE group_id=? ORDER BY position", groupId)) {
            Booking b = bookings.findById(((Number) member.get("booking_id")).longValue()).orElseThrow();
            members.add(new MemberView(b.getId(), b.getBookingReference(), ((Number) member.get("position")).intValue(),
                    (String) member.get("guest_name"), Boolean.TRUE.equals(member.get("is_booker")) || Objects.equals(member.get("is_booker"), 1),
                    b.getService().getId(), b.getService().getName(), SessionAllocation.customerDisplay(spa, b.getTherapist()),
                    b.getStartTs(), b.getEndTs(), b.getStatus().name(), b.getPriceCents(), nz(b.getDiscountCents()), nz(b.getTaxCents()),
                    nz(b.getGiftCardCents()), nz(b.getRemainderCents())));
        }
        OffsetDateTime start = members.isEmpty() ? null : members.get(0).startTs();
        return new GroupView(groupId, spa.getId(), spa.getName(), ((Number) group.get("group_size")).intValue(), start,
                (String) group.get("payment_mode"), (String) group.get("currency"), ((Number) group.get("total_cents")).intValue(),
                (String) group.get("payment_status"), members, null);
    }

    private static int nz(Integer value) { return value == null ? 0 : value; }

    // ---------------- cancelling the whole group ----------------

    private List<Long> active(long groupId) {
        return jdbc.queryForList("""
                SELECT m.booking_id FROM booking_group_member m JOIN booking b ON b.id=m.booking_id
                WHERE m.group_id=? AND b.status IN ('PENDING_PAYMENT','CONFIRMED') ORDER BY m.position
                """, Long.class, groupId);
    }

    private String guestName(long bookingId) {
        return jdbc.queryForObject("SELECT guest_name FROM booking_group_member WHERE booking_id=?", String.class, bookingId);
    }

    /** Each remaining guest's cancellation terms, by the same rules as cancelling that booking alone. */
    @Transactional
    public GroupQuote quote(long groupId, Long userId) {
        var group = view(groupId, userId);
        var ids = active(groupId);
        if (ids.isEmpty()) throw new IllegalStateException("Nothing left to cancel in this group");
        List<MemberQuote> quotes = new ArrayList<>();
        int refund = 0, fee = 0;
        for (Long id : ids) {
            var q = bookingService.cancellationQuote(id, userId);
            quotes.add(new MemberQuote(id, guestName(id), q));
            refund += q.refundCents() + q.walletRefundCents() + q.giftCardRefundCents();
            fee += q.feeCents() + q.walletFeeCents() + q.giftCardFeeCents();
        }
        return new GroupQuote(groupId, group.currency(), refund, fee, quotes);
    }

    /**
     * Cancels every remaining guest. One transaction: if any guest cannot be cancelled under the policy,
     * nobody is. {@code expectedRefundCents} is what the booker was shown; a change is refused.
     */
    @Transactional
    public GroupQuote cancel(long groupId, Long userId, Integer expectedRefundCents) {
        var quote = quote(groupId, userId);
        if (expectedRefundCents != null && expectedRefundCents != quote.refundCents())
            throw new IllegalStateException("Cancellation terms changed. Review the updated amounts before confirming.");
        for (var member : quote.members()) bookingService.cancelBooking(member.bookingId(), userId, member.quote());
        return quote;
    }

    // ---------------- the spa's view ----------------

    public record DeskMember(long bookingId, int position, String guestName, String guestPhone, boolean booker) {}

    public record DeskGroup(long groupId, int size, String bookerName, List<DeskMember> members) {}

    @Transactional(readOnly = true)
    public List<DeskGroup> forSpa(long spaId, OffsetDateTime from, OffsetDateTime to) {
        var groups = jdbc.queryForList("""
                SELECT g.id, g.group_size, u.name FROM booking_group g JOIN app_user u ON u.id=g.booker_user_id
                WHERE g.spa_id=? AND g.start_ts>=? AND g.start_ts<? ORDER BY g.start_ts, g.id
                """, spaId, Timestamp.valueOf(from.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()),
                Timestamp.valueOf(to.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()));
        List<DeskGroup> result = new ArrayList<>();
        for (var g : groups) {
            long id = ((Number) g.get("id")).longValue();
            var members = jdbc.query("SELECT booking_id, position, guest_name, guest_phone, is_booker FROM booking_group_member WHERE group_id=? ORDER BY position",
                    (rs, n) -> new DeskMember(rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getBoolean(5)), id);
            result.add(new DeskGroup(id, ((Number) g.get("group_size")).intValue(), (String) g.get("name"), members));
        }
        return result;
    }

    /** Owner setting: a "couples room / side by side" service and how many guests fit at once (null turns it off). */
    @Transactional
    public Integer setSideBySide(long spaId, long serviceId, Integer capacity) {
        if (capacity != null && (capacity < 2 || capacity > GroupAvailability.MAX_GUESTS))
            throw new IllegalArgumentException("Side-by-side capacity must be 2 to " + GroupAvailability.MAX_GUESTS);
        if (jdbc.update("UPDATE service SET side_by_side_capacity=? WHERE id=? AND spa_id=?", capacity, serviceId, spaId) == 0)
            throw new EntityNotFoundException("Service not found");
        return capacity;
    }
}
