package com.nirvana.application.service.impl;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.BookingHold;
import com.nirvana.application.model.Slot;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.Therapist;
import com.nirvana.application.model.User;
import com.nirvana.application.model.dto.CancellationQuote;
import com.nirvana.application.model.dto.BookingCancelResponse;
import com.nirvana.application.model.dto.BookingCreateRequest;
import com.nirvana.application.model.dto.BookingCreateResponse;
import com.nirvana.application.model.dto.BookingListResponse;
import com.nirvana.application.model.dto.BookingRescheduleRequest;
import com.nirvana.application.model.dto.BookingRescheduleResponse;
import com.nirvana.application.model.dto.BookingSummaryResponse;
import com.nirvana.application.model.dto.PagedResponse;
import com.nirvana.application.model.dto.PackageUsageConsumeRequest;
import com.nirvana.application.model.enums.BookingPaymentType;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.BookingSource;
import com.nirvana.application.model.enums.CancellationActor;
import com.nirvana.application.model.enums.PaymentMode;
import com.nirvana.application.model.enums.PaymentStatus;
import com.nirvana.application.model.enums.RefundRoute;
import com.nirvana.application.model.enums.RefundStatus;
import com.nirvana.application.model.enums.SlotStatus;
import com.nirvana.application.repository.BookingHoldRepository;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.PaymentRepository;
import com.nirvana.application.repository.ServiceRepository;
import com.nirvana.application.repository.SlotRepository;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.repository.TherapistRepository;
import com.nirvana.application.repository.UserRepository;
import com.nirvana.application.service.BookingService;
import com.nirvana.application.service.NotificationService;
import com.nirvana.application.service.PackageSubscriptionService;
import com.nirvana.application.service.PricingService;
import com.nirvana.application.service.RefundService;
import com.nirvana.application.service.WaitlistService;
import com.nirvana.application.utils.CancellationEligibility;
import com.nirvana.application.utils.CancellationEvaluator;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    private static final int CANCELLATION_CUTOFF_MINUTES = 15;
    private static final int SAME_DAY_RESCHEDULE_CUTOFF_MINUTES = 60;
    private static final int MAX_RESCHEDULES = 2;

    private final org.springframework.jdbc.core.JdbcTemplate operationsDb;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    private final SpaRepository spaRepository;
    private final ServiceRepository serviceRepository;
    private final SlotRepository slotRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final com.nirvana.application.service.NotificationSchedulingService notifications;
    private final PackageSessionRestorer packageSessions;
    private final LoyaltyService loyalty;
    private final RefundService refundService;
    private final PricingService pricingService;
    private final BookingHoldRepository bookingHoldRepository;
    private final WaitlistService waitlistService;
    private final TherapistRepository therapistRepository;
    private final TherapistRoster roster;
    private final SessionAllocation allocation;
    private final SlotAvailabilityService availability;
    private final PackageSubscriptionService packageSubscriptionService;
    private final com.nirvana.application.service.SpaPackageUsageService spaPackageUsageService;

    @Override
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public BookingCreateResponse createBooking(Long userId, BookingCreateRequest request) {
        return create(userId, request, false);
    }

    /** Desk controller checks spa authority and constructs an offline, single-guest request. */
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public BookingCreateResponse createFromDesk(Long userId, BookingCreateRequest request) {
        return create(userId, request, true);
    }

    private BookingCreateResponse create(Long userId, BookingCreateRequest request, boolean desk) {
        if (request == null) {
            throw new IllegalArgumentException("Request must not be null");
        }
        int guests = request.getGuests() != null ? request.getGuests() : 1;
        if (guests <= 0) {
            throw new IllegalArgumentException("guests must be > 0");
        }

        log.info("event=booking.validate_spa");
        Spa spa = spaRepository.lockSchedule(request.getSpaId())
                .orElseThrow(() -> new EntityNotFoundException("Spa not found: " + request.getSpaId()));
        String key = request.getRequestKey();
        String fingerprint = null;
        if (key != null) {
            var fields = (com.fasterxml.jackson.databind.node.ObjectNode) json.valueToTree(request);
            fields.remove("holdToken");
            fingerprint = org.apache.commons.codec.digest.DigestUtils.sha256Hex(fields.toString());
            var attempts = operationsDb.queryForList("SELECT booking_id,fingerprint FROM checkout_attempt WHERE spa_id=? AND user_id=? AND request_key=?",spa.getId(),userId,key);
            if (!attempts.isEmpty()) {
                var attempt=attempts.get(0);
                if (!fingerprint.equals(attempt.get("fingerprint"))) throw new IllegalStateException("Checkout key already used for different booking details");
                var existing=bookingRepository.findById(((Number)attempt.get("booking_id")).longValue()).orElseThrow();
                var response=buildResponse(existing,existing.getRemainderCents());
                return response;
            }
        }
        if (!desk && !"INSTANT".equals(spa.getBookingMode())) throw new IllegalStateException("Contact the spa or request acceptance before booking");
        if (Boolean.FALSE.equals(spa.getIsActive())) {
            throw new IllegalStateException("Spa is inactive");
        }
        if (Boolean.FALSE.equals(spa.getIsVerified())) {
            throw new IllegalStateException("Spa is not verified");
        }

        com.nirvana.application.model.Service service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new EntityNotFoundException("Service not found: " + request.getServiceId()));
        if (!service.getSpa().getId().equals(spa.getId())) {
            throw new IllegalArgumentException("Service does not belong to Spa");
        }
        if (Boolean.FALSE.equals(service.getIsActive())) {
            throw new IllegalStateException("Service is inactive");
        }
        if (guests < service.getMinPersons() || guests > service.getMaxPersons()) {
            throw new IllegalArgumentException("guests must be between minPersons and maxPersons for the service");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        log.info("event=booking.lock_slot");
        Slot slot = slotRepository.findByIdForUpdate(request.getSlotId())
                .orElseThrow(() -> new EntityNotFoundException("Slot not found: " + request.getSlotId()));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        bookingHoldRepository.deleteByExpiresAtBefore(now);

        BookingHold hold = null;
        if (request.getHoldToken() != null && !request.getHoldToken().isBlank()) {
            hold = bookingHoldRepository.findByHoldToken(request.getHoldToken())
                    .orElseThrow(() -> new IllegalStateException(SessionAllocation.TAKEN+"; your hold expired"));
            if (hold.getExpiresAt().isBefore(now)) {
                throw new IllegalStateException(SessionAllocation.TAKEN+"; your hold expired");
            }
            if (!hold.getSlot().getId().equals(slot.getId())) {
                throw new IllegalArgumentException("Hold does not belong to slot");
            }
            if (hold.getUser() != null && !hold.getUser().getId().equals(userId)) {
                throw new IllegalArgumentException("Hold belongs to a different user");
            }
            if (hold.getHoldUnits()!=guests) throw new IllegalStateException("Guest count differs from the reservation");
            if (hold.getRoom()!=null && (slot.getRoom()==null || !hold.getRoom().getId().equals(slot.getRoom().getId()))) throw new IllegalStateException(SessionAllocation.TAKEN);
            if (hold.isConvertedToBooking()) {
                throw new IllegalStateException("Hold already used for booking");
            }
        }

        int activeHolds = Optional.ofNullable(bookingHoldRepository.findActiveHoldUnits(slot.getId(), now)).orElse(0);
        int holdsToIgnore = hold != null && hold.getHoldUnits() != null ? hold.getHoldUnits() : 0;
        validateSlot(spa, service, slot, guests, Math.max(0, activeHolds - holdsToIgnore));

        log.info("event=booking.allocate_therapist");
        Long preferred = request.getTherapistId();
        String label=request.getTherapistType();
        if (hold != null && preferred == null && label == null) {
            if ("NAMED".equals(hold.getTherapistPreference())) preferred=hold.getTherapist().getId();
            label=hold.getRequestedLabel();
        }
        String mode=SessionAllocation.preference(preferred,label);
        // Public naming preferences do not constrain a receptionist assigning their own team.
        // allocation.choose still enforces spa, service, shift and overlapping appointments.
        if (!desk) allocation.validatePreference(spa,preferred,label);
        if(hold!=null && hold.getTherapistPreference()!=null){
            if(!mode.equals(hold.getTherapistPreference()) || !java.util.Objects.equals(label,hold.getRequestedLabel()))
                throw new IllegalStateException(SessionAllocation.TAKEN+"; reserve your updated preference first");
        }
        if(hold!=null && hold.getTherapist()!=null){
            if(preferred!=null && !preferred.equals(hold.getTherapist().getId())) throw new IllegalStateException("Choose the therapist reserved by this hold");
            preferred=hold.getTherapist().getId();
        }
        Therapist therapist=allocation.choose(slot,preferred,label,hold==null?null:hold.getId(),null);
        String requestedTherapistType=therapist.getType();

        PaymentMode paymentMode = PaymentMode.valueOf(request.getPaymentMode().toUpperCase());
        boolean spaPackage = Boolean.TRUE.equals(request.getPayWithSpaPackage());
        if (spaPackage && Boolean.TRUE.equals(request.getPayWithPackage())) throw new IllegalArgumentException("Choose one package payment option");
        boolean payWithPackage = spaPackage || Boolean.TRUE.equals(request.getPayWithPackage());
        if (payWithPackage) {
            paymentMode = PaymentMode.OFFLINE;
            if (spaPackage) {
                var eligibility = spaPackageUsageService.canUseSpaPackage(userId, spa.getId());
                if (!eligibility.isCanUse()) throw new IllegalStateException(eligibility.getReason());
            } else {
            var eligibility = packageSubscriptionService.canUsePackage(userId, spa.getId());
            if (!eligibility.isCanUse()) {
                String reason = eligibility.getReason() != null
                        ? eligibility.getReason()
                        : "Package not eligible for this booking";
                throw new IllegalStateException(reason);
            }
            }
        }
        String paymentMethod = request.getPaymentMethod() != null
                ? request.getPaymentMethod().trim().toUpperCase()
                : "RAZORPAY";

        int unitPrice = service.getPriceCents() != null
                ? service.getPriceCents()
                : (service.getBasePriceCents() != null ? service.getBasePriceCents() : 0);
        int priceCents = unitPrice * guests;

        boolean redeemLoyalty = Boolean.TRUE.equals(request.getRedeemLoyaltyPoints());
        PricingService.PricingResult pricing = pricingService.evaluatePricing(
                user,
                spa,
                service,
                slot,
                guests,
                priceCents,
                request.getCouponCode(),
                redeemLoyalty);

        int discountCents = pricing.discountCents();
        int taxCents = calculateTax(spa, priceCents - discountCents);
        int totalCents = priceCents + taxCents - discountCents;
        int payableCents = payWithPackage ? 0 : totalCents;
        BookingSource bookingSource = resolveBookingSource(request.getBookingSource());

        Booking booking = new Booking();
        booking.setSpa(spa);
        booking.setService(service);
        booking.setSlot(slot);
        booking.setUser(user);
        booking.setBookingReference(generateUniqueBookingReference());
        booking.setCurrency(service.getCurrency() != null ? service.getCurrency() : spa.getDefaultCurrency());
        booking.setGuestCount(guests);
        booking.setPriceCents(priceCents);
        booking.setDiscountCents(discountCents);
        booking.setCouponCode(pricing.appliedCouponCode());
        booking.setCoupon(pricing.coupon());
        booking.setLoyaltyPointsRedeemed(pricing.loyaltyPointsRedeemed());
        booking.setTaxCents(taxCents);
        booking.setDepositCents(0);
        booking.setRemainderCents(payableCents);
        booking.setRefundStatus(RefundStatus.NONE);
        booking.setCustomerNotes(request.getSpecialRequest());
        booking.setPolicySnapshotJson(service.getCancellationPolicyJson() != null
                ? service.getCancellationPolicyJson()
                : "{\"defaultCutoffMinutes\":" + CANCELLATION_CUTOFF_MINUTES + ",\"defaultFeePercent\":0,\"defaultRoute\":\"ORIGINAL_METHOD\"}");
        booking.setStartTs(slot.getStartTs());
        booking.setEndTs(TherapistRoster.end(slot));
        booking.setScheduledBy(desk ? CancellationActor.SPA : CancellationActor.USER);
        booking.setLastStatusChangedAt(now);
        booking.setPaymentMode(paymentMode);
        booking.setPaymentType(payWithPackage ? BookingPaymentType.PAID_BY_PACKAGE : BookingPaymentType.STANDARD);
        booking.setRoom(slot.getRoom());
        booking.setTherapistPreference(mode);
        booking.setTherapist(therapist);
        booking.setTherapistType(requestedTherapistType);
        booking.setBookingSource(bookingSource);

        if (paymentMode == PaymentMode.OFFLINE) {
            booking.setStatus(BookingStatus.CONFIRMED);
        } else {
            booking.setStatus(BookingStatus.PENDING_PAYMENT);
        }

        log.info("event=booking.persist");
        Booking saved = bookingRepository.save(booking);
        if (desk) {
            operationsDb.update("INSERT INTO spa_booking_confirmation(booking_id,spa_id,token,status,responded_at,created_at,updated_at) VALUES(?,?,?,'CONFIRMED',UTC_TIMESTAMP(),UTC_TIMESTAMP(),UTC_TIMESTAMP())",
                    saved.getId(),spa.getId(),UUID.randomUUID().toString().replace("-",""));
        }

        if (hold != null) {
            hold.setConvertedToBooking(true);
            bookingHoldRepository.save(hold);
        }

        if (pricing.loyaltyPointsRedeemed() > 0) {
            int remainingPoints = Math.max(0, (user.getLoyaltyPoints() == null ? 0 : user.getLoyaltyPoints()) - pricing.loyaltyPointsRedeemed());
            user.setLoyaltyPoints(remainingPoints);
            userRepository.save(user);
        }

        // reserve capacity immediately to prevent overbooking even while payment is pending
        short updatedUnits = (short) (slot.getBookedUnits() + guests);
        slot.setBookedUnits(updatedUnits);
        if (updatedUnits >= slot.getCapacityUnit()) {
            slot.setStatus(SlotStatus.BOOKED);
        }
        slotRepository.save(slot);

        com.nirvana.application.model.dto.PaymentInitResponse razorpayInit = null;
        com.nirvana.application.model.dto.UpiPaymentInitResponse upiInit = null;
        if (paymentMode == PaymentMode.ONLINE) {
            if ("UPI".equals(paymentMethod)) {
                upiInit = paymentService.initiateUpiPayment(saved.getId(), "booking-" + saved.getId() + "-upi-init");
            } else {
                razorpayInit = paymentService.initiateRazorpayPayment(saved.getId(), "booking-" + saved.getId() + "-razorpay-init");
            }
        }

        if (payWithPackage) {
            PackageUsageConsumeRequest consumeRequest = new PackageUsageConsumeRequest();
            consumeRequest.setBookingId(saved.getId());
            consumeRequest.setSpaId(spa.getId());
            if (spaPackage) spaPackageUsageService.consumeSession(userId, spa.getId(), saved.getId(), "Booking checkout");
            else packageSubscriptionService.consumePackageSession(userId, consumeRequest);
            saved = bookingRepository.findById(saved.getId())
                    .orElse(saved);
        }

        // Pay-at-spa and package bookings are confirmed here; online bookings are confirmed
        // (and announced) when the payment is captured.
        if (saved.getStatus() == BookingStatus.CONFIRMED) {
            notifications.scheduleBookingNotifications(saved);
        }

        BookingCreateResponse response = buildResponse(saved, payableCents);
        if (key != null) operationsDb.update("INSERT INTO checkout_attempt(spa_id,user_id,request_key,fingerprint,booking_id) VALUES(?,?,?,?,?)",
                spa.getId(),userId,key,fingerprint,saved.getId());
        response.setRazorpay(razorpayInit);
        response.setUpi(upiInit);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public BookingListResponse listUserBookings(Long userId, BookingStatus status, Pageable pageable) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Page<Booking> bookings = status == null
                ? bookingRepository.findWithDetailsByUserId(userId, pageable)
                : bookingRepository.findWithDetailsByUserIdAndStatus(userId, status, pageable);

        List<BookingSummaryResponse> content = bookings.stream()
                .map(booking -> toSummaryResponse(booking, CancellationEvaluator.evaluateCancellation(booking, now, CANCELLATION_CUTOFF_MINUTES)))
                .toList();

        PagedResponse<BookingSummaryResponse> page = new PagedResponse<>(
                content,
                bookings.getNumber(),
                bookings.getSize(),
                bookings.getTotalElements(),
                bookings.getTotalPages());

        return new BookingListResponse(page);
    }

    @Override
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public BookingRescheduleResponse rescheduleBooking(Long bookingId, Long userId, BookingRescheduleRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Booking booking = bookingRepository.findOwnedForUpdate(bookingId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found for user"));

        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.COMPLETED || booking.getStatus() == BookingStatus.NO_SHOW) {
            throw new IllegalStateException("Booking cannot be rescheduled in current status: " + booking.getStatus());
        }
        if (booking.getRescheduleCount() != null && booking.getRescheduleCount() >= MAX_RESCHEDULES) {
            throw new IllegalStateException("Maximum reschedule limit reached");
        }
        if (booking.getStartTs().isBefore(now)) {
            throw new IllegalStateException("Cannot reschedule a past booking");
        }
        if (booking.getStartTs().toLocalDate().equals(now.toLocalDate())
                && (request.getAllowSameDayChange() == null || !request.getAllowSameDayChange())
                && booking.getStartTs().isBefore(now.plusMinutes(SAME_DAY_RESCHEDULE_CUTOFF_MINUTES))) {
            throw new IllegalStateException("Same-day changes are locked within cutoff window");
        }
        return moveToSlot(booking, request, now, true);
    }

    /**
     * The spa moves its own appointment. The guest's limits (two changes, the same-day cutoff)
     * are for the guest, so they don't apply and the move doesn't use up the guest's changes.
     */
    @Override
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public BookingRescheduleResponse rescheduleBySpa(Long bookingId, Long spaId, BookingRescheduleRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .filter(b -> b.getSpa().getId().equals(spaId))
                .orElseThrow(() -> new EntityNotFoundException("Booking not found for spa"));
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only a confirmed appointment can be moved");
        }
        if (!booking.getStartTs().isAfter(now)) {
            throw new IllegalStateException("Cannot move an appointment that has started");
        }
        return moveToSlot(booking, request, now, false);
    }

    private BookingRescheduleResponse moveToSlot(Booking booking, BookingRescheduleRequest request, OffsetDateTime now, boolean byGuest) {
        spaRepository.lockSchedule(booking.getSpa().getId()).orElseThrow();
        Slot targetSlot = slotRepository.findByIdForUpdate(request.getTargetSlotId())
                .orElseThrow(() -> new EntityNotFoundException("Target slot not found"));
        Slot currentSlot = slotRepository.findByIdForUpdate(booking.getSlot().getId())
                .orElseThrow(() -> new EntityNotFoundException("Current slot not found"));

        Spa spa = booking.getSpa();
        com.nirvana.application.model.Service service = booking.getService();

        int activeHolds = Optional.ofNullable(bookingHoldRepository.findActiveHoldUnits(targetSlot.getId(), now)).orElse(0);
        validateSlot(spa, service, targetSlot, booking.getGuestCount(), activeHolds);

        boolean change=Boolean.TRUE.equals(request.getChangeTherapistPreference());
        String mode=change?SessionAllocation.preference(request.getTherapistId(),request.getTherapistType()):booking.getTherapistPreference();
        Long named=change?request.getTherapistId():("NAMED".equals(mode)||mode==null) && booking.getTherapist()!=null?booking.getTherapist().getId():null;
        String label=change?request.getTherapistType():"LABEL".equals(mode)?booking.getTherapistType():null;
        if(change)allocation.validatePreference(spa,named,label);
        var replacement=allocation.choose(targetSlot,named,label,null,booking.getId());
        booking.setTherapistPreference(mode);
        booking.setRoom(targetSlot.getRoom());
        booking.setTherapist(replacement);
        booking.setTherapistType(replacement.getType());
        releaseCapacity(currentSlot, booking.getGuestCount());
        short updatedUnits = (short) (targetSlot.getBookedUnits() + booking.getGuestCount());
        targetSlot.setBookedUnits(updatedUnits);
        if (updatedUnits >= targetSlot.getCapacityUnit()) {
            targetSlot.setStatus(SlotStatus.BOOKED);
        }

        booking.setSlot(targetSlot);
        booking.setStartTs(targetSlot.getStartTs());
        booking.setEndTs(TherapistRoster.end(targetSlot));
        if (byGuest) booking.setRescheduleCount((booking.getRescheduleCount() == null ? 0 : booking.getRescheduleCount()) + 1);
        booking.setLastStatusChangedAt(now);
        booking.setCancellationReasonText(request.getReason());
        booking.setReminderProcessedAt(null); // the new time gets its own reminder
        bookingRepository.save(booking);

        slotRepository.save(currentSlot);
        slotRepository.save(targetSlot);
        waitlistService.tryNotifySlotAvailable(currentSlot);
        notifications.bookingRescheduled(booking);

        return new BookingRescheduleResponse(booking.getId(), booking.getStatus(), booking.getStartTs(), booking.getEndTs(), booking.getRescheduleCount());
    }

    @Override
    @Transactional
    public CancellationQuote cancellationQuote(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found for user"));
        var policy = CancellationEvaluator.evaluateCancellation(booking, OffsetDateTime.now(ZoneOffset.UTC), CANCELLATION_CUTOFF_MINUTES);
        if (!policy.canCancel()) throw new IllegalStateException(policy.reason());
        return quote(booking, policy, capturedPayment(booking).orElse(null));
    }

    private Optional<com.nirvana.application.model.Payment> capturedPayment(Booking booking) {
        if (booking.getPaymentMode() != PaymentMode.ONLINE) return Optional.empty();
        return paymentRepository.findFirstByBookingIdAndPaymentStatusIn(booking.getId(),
                List.of(PaymentStatus.COMPLETED, PaymentStatus.CAPTURED, PaymentStatus.PARTIALLY_REFUNDED));
    }

    private CancellationQuote quote(Booking booking, CancellationEligibility policy, com.nirvana.application.model.Payment payment) {
        return CancellationQuote.calculate(payment == null ? 0 : payment.getAmountCents(),
                payment == null ? 0 : Optional.ofNullable(payment.getRefundedCents()).orElse(0),
                Optional.ofNullable(policy.feePercent()).orElse(0), payment == null ? booking.getCurrency() : payment.getCurrency(),
                Optional.ofNullable(policy.refundRoute()).orElse(RefundRoute.ORIGINAL_METHOD),
                booking.getPaymentType() == BookingPaymentType.PAID_BY_PACKAGE);
    }

    @Override
    @Transactional
    public BookingCancelResponse cancelBooking(Long bookingId, Long userId, CancellationQuote accepted) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        Booking booking = bookingRepository.findOwnedForUpdate(bookingId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found for user"));

        if (booking.getStatus() == BookingStatus.CANCELLED
                || booking.getStatus() == BookingStatus.COMPLETED
                || booking.getStatus() == BookingStatus.NO_SHOW) {
            throw new IllegalStateException("Booking cannot be cancelled in current status: " + booking.getStatus());
        }

        CancellationEligibility eligibility = CancellationEvaluator.evaluateCancellation(
                booking, now, CANCELLATION_CUTOFF_MINUTES);
        if (!eligibility.canCancel()) {
            throw new IllegalStateException(eligibility.reason());
        }

        var payment = capturedPayment(booking).orElse(null);
        var quote = quote(booking, eligibility, payment);
        if (accepted != null && !accepted.equals(quote)) {
            throw new IllegalStateException("Cancellation terms changed. Review the updated amounts before confirming.");
        }
        booking.setRefundRoute(quote.route());
        Integer refundAmountCents = quote.refundCents();
        boolean refundInitiated = refundAmountCents > 0;
        if (refundInitiated) {
            int total = Optional.ofNullable(payment.getRefundedCents()).orElse(0) + refundAmountCents;
            refundService.processRefund(payment, booking, quote.route(), refundAmountCents);
            payment.setRefundedCents(total);
            payment.setPaymentStatus(total == payment.getAmountCents() ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
            payment.setRefundedAt(now);
            paymentRepository.save(payment);
            booking.setRefundStatus(quote.route() == RefundRoute.WALLET_CREDIT ? RefundStatus.COMPLETED : RefundStatus.PROCESSING);
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(now);
        booking.setCancellationReasonText("User cancelled booking");
        booking.setCancelledBy(CancellationActor.USER);
        booking.setLastStatusChangedAt(now);
        packageSessions.restoreFor(booking);
        loyalty.returnRedeemed(booking);
        bookingRepository.save(booking);

        Slot slot = slotRepository.findByIdForUpdate(booking.getSlot().getId())
                .orElseThrow(() -> new EntityNotFoundException("Slot not found for booking"));
        releaseCapacity(slot, booking.getGuestCount());
        slotRepository.save(slot);
        waitlistService.tryNotifySlotAvailable(slot);

        notifications.bookingCancelled(booking);

        return BookingCancelResponse.builder()
                .bookingId(booking.getId())
                .bookingReference(booking.getBookingReference())
                .status(booking.getStatus())
                .refundInitiated(refundInitiated)
                .refundAmountCents(refundAmountCents)
                .refundStatus(booking.getRefundStatus())
                .refundRoute(booking.getRefundRoute())
                .build();
    }

    @Override
    @Transactional
    public BookingCancelResponse cancelBookingBySpa(Long spaId, Long bookingId, String reason, boolean byAdmin) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .filter(b -> b.getSpa().getId().equals(spaId))
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("A booking that is "
                    + booking.getStatus().name().toLowerCase().replace('_', ' ') + " cannot be cancelled");
        }
        if (!now.isBefore(booking.getEndTs())) {
            throw new IllegalStateException("This appointment has ended. Mark it completed or as a no-show instead.");
        }

        // The spa is the one backing out, so the customer is made whole: full refund, no fee.
        Integer refundAmountCents = null;
        var payments = paymentRepository.findByBookingId(bookingId);
        for (var payment : payments) {
            if (payment.getPaymentStatus() == PaymentStatus.CAPTURED || payment.getPaymentStatus() == PaymentStatus.COMPLETED) {
                int refundable = payment.getAmountCents() - Optional.ofNullable(payment.getRefundedCents()).orElse(0);
                if (refundable > 0) {
                    paymentService.refundAuthorizedBookingPayment(bookingId, refundable, "Cancelled by the spa");
                    payment.setPaymentStatus(PaymentStatus.REFUNDED);
                    payment.setRefundedCents(payment.getAmountCents());
                    payment.setRefundedAt(now);
                    booking.setRefundStatus(RefundStatus.COMPLETED);
                    refundAmountCents = refundable;
                }
            } else if (payment.getPaymentStatus() == PaymentStatus.INIT || payment.getPaymentStatus() == PaymentStatus.PENDING) {
                // A capture that still arrives is recorded and flagged for refund by the payment flow.
                payment.setPaymentStatus(PaymentStatus.CANCELLED);
            }
            paymentRepository.save(payment);
        }

        booking.setRefundRoute(RefundRoute.ORIGINAL_METHOD);
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(now);
        booking.setCancelledBy(byAdmin ? CancellationActor.ADMIN : CancellationActor.SPA);
        booking.setCancellationReasonCode("SPA_CANCELLED");
        booking.setCancellationReasonText(reason);
        booking.setLastStatusChangedAt(now);
        packageSessions.restoreFor(booking);
        loyalty.returnRedeemed(booking);
        bookingRepository.save(booking);

        Slot slot = slotRepository.findByIdForUpdate(booking.getSlot().getId())
                .orElseThrow(() -> new EntityNotFoundException("Slot not found for booking"));
        releaseCapacity(slot, booking.getGuestCount());
        slotRepository.save(slot);
        if (slot.getStartTs().isAfter(now)) {
            waitlistService.tryNotifySlotAvailable(slot);
        }
        notifications.bookingCancelled(booking);

        return BookingCancelResponse.builder()
                .bookingId(booking.getId())
                .bookingReference(booking.getBookingReference())
                .status(booking.getStatus())
                .refundInitiated(refundAmountCents != null)
                .refundAmountCents(refundAmountCents)
                .refundStatus(booking.getRefundStatus())
                .refundRoute(booking.getRefundRoute())
                .build();
    }

    private BookingSource resolveBookingSource(String source) {
        if (!StringUtils.hasText(source)) {
            return BookingSource.WEB;
        }
        try {
            return BookingSource.valueOf(source.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid bookingSource: " + source);
        }
    }

    private BookingSummaryResponse toSummaryResponse(Booking booking, CancellationEligibility eligibility) {
        String mode = booking.getTherapistPreference();
        String original = "";
        if ((mode == null || "NAMED".equals(mode)) && booking.getTherapist() != null)
            original = String.valueOf(booking.getTherapist().getId());
        else if ("LABEL".equals(mode)) original = "type:" + booking.getTherapistType();
        return BookingSummaryResponse.builder().originalPreference(original).therapistName(SessionAllocation.customerDisplay(booking.getSpa(),booking.getTherapist()))
                .bookingId(booking.getId())
                .bookingReference(booking.getBookingReference())
                .spaId(booking.getSpa().getId())
                .spaName(booking.getSpa().getName())
                .spaCity(booking.getSpa().getAddress() != null ? booking.getSpa().getAddress().getCity() : null)
                .spaAddressLine(booking.getSpa().getAddress() != null ? booking.getSpa().getAddress().getFormattedAddress() : null)
                .serviceId(booking.getService().getId())
                .serviceName(booking.getService().getName())
                .serviceDurationMinutes(booking.getService().getDurationMin())
                .servicePriceCents(booking.getService().getPriceCents())
                .startTs(booking.getStartTs())
                .endTs(booking.getEndTs())
                .status(booking.getStatus())
                .paymentMode(booking.getPaymentMode())
                .invoiceAvailable(paymentRepository.existsByBookingIdAndPaymentStatusIn(booking.getId(), java.util.List.of(PaymentStatus.CAPTURED, PaymentStatus.COMPLETED, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED)))
                .canCancel(eligibility.canCancel())
                .refundStatus(booking.getRefundStatus().name())
                .cancellationReason(eligibility.reason())
                .build();
    }

    private void validateSlot(Spa spa, com.nirvana.application.model.Service service, Slot slot, int guests, int activeHolds) {
        if (!slot.getSpa().getId().equals(spa.getId())) {
            throw new IllegalArgumentException("Slot does not belong to Spa");
        }
        if (!slot.getService().getId().equals(service.getId())) {
            throw new IllegalArgumentException("Slot does not belong to Service");
        }
        availability.requireBookable(slot,guests,activeHolds);
    }

    private void releaseCapacity(Slot slot, int guests) {
        int updated = Math.max(0, slot.getBookedUnits() - guests);
        slot.setBookedUnits((short) updated);
        if (slot.getStatus() == SlotStatus.BOOKED && updated < slot.getCapacityUnit()) {
            slot.setStatus(SlotStatus.OPEN);
        }
    }

    private int calculateTax(Spa spa, int taxableAmount) {
        Integer taxPercent = spa.getTaxPercent();
        if (taxPercent == null || taxPercent <= 0) {
            return 0;
        }
        return (int) Math.round(taxableAmount * (taxPercent / 100.0));
    }

    private BookingCreateResponse buildResponse(Booking booking, int totalCents) {
        BookingCreateResponse response = new BookingCreateResponse();
        response.setTherapistName(SessionAllocation.customerDisplay(booking.getSpa(),booking.getTherapist()));
        response.setBookingId(booking.getId());
        response.setBookingReference(booking.getBookingReference());
        response.setStatus(booking.getStatus().name());
        response.setPaymentMode(booking.getPaymentMode() != null ? booking.getPaymentMode().name() : null);
        response.setPriceCents(booking.getPriceCents());
        response.setTaxCents(booking.getTaxCents());
        response.setDiscountCents(booking.getDiscountCents());
        response.setTotalCents(totalCents);
        response.setCurrency(booking.getCurrency());
        response.setPaymentType(booking.getPaymentType() != null ? booking.getPaymentType().name() : null);
        response.setPackageSubscriptionId(
                booking.getPackageSubscription() != null ? booking.getPackageSubscription().getId() : null);
        return response;
    }

    private String generateUniqueBookingReference() {
        while (true) {
            String ref = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
            if (!bookingRepository.existsByBookingReference(ref)) {
                return ref;
            }
        }
    }
}
