package com.nirvana.application.service.impl;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.Slot;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.User;
import com.nirvana.application.model.dto.BookingCancelResponse;
import com.nirvana.application.model.dto.BookingCreateRequest;
import com.nirvana.application.model.dto.BookingCreateResponse;
import com.nirvana.application.model.dto.BookingListResponse;
import com.nirvana.application.model.dto.BookingSummaryResponse;
import com.nirvana.application.model.dto.PagedResponse;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.CancellationActor;
import com.nirvana.application.model.enums.PaymentMode;
import com.nirvana.application.model.enums.PaymentStatus;
import com.nirvana.application.model.enums.RefundStatus;
import com.nirvana.application.model.enums.SlotStatus;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.PaymentRepository;
import com.nirvana.application.repository.ServiceRepository;
import com.nirvana.application.repository.SlotRepository;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.repository.UserRepository;
import com.nirvana.application.service.BookingService;
import com.nirvana.application.service.NotificationService;
import com.nirvana.application.service.PricingService;
import com.nirvana.application.service.RefundService;
import com.nirvana.application.utils.CancellationEligibility;
import com.nirvana.application.utils.CancellationEvaluator;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    private static final int CANCELLATION_CUTOFF_MINUTES = 15;

    private final SpaRepository spaRepository;
    private final ServiceRepository serviceRepository;
    private final SlotRepository slotRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final RefundService refundService;
    private final PricingService pricingService;

    @Override
    @Transactional
    public BookingCreateResponse createBooking(Long userId, BookingCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request must not be null");
        }
        int guests = request.getGuests() != null ? request.getGuests() : 1;
        if (guests <= 0) {
            throw new IllegalArgumentException("guests must be > 0");
        }

        Spa spa = spaRepository.findById(request.getSpaId())
                .orElseThrow(() -> new EntityNotFoundException("Spa not found: " + request.getSpaId()));
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

        Slot slot = slotRepository.findByIdForUpdate(request.getSlotId())
                .orElseThrow(() -> new EntityNotFoundException("Slot not found: " + request.getSlotId()));

        validateSlot(spa, service, slot, guests);

        PaymentMode paymentMode = PaymentMode.valueOf(request.getPaymentMode().toUpperCase());

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
        booking.setTaxCents(taxCents);
        booking.setDepositCents(0);
        booking.setRemainderCents(totalCents);
        booking.setRefundStatus(RefundStatus.NONE);
        booking.setCustomerNotes(request.getSpecialRequest());
        booking.setStartTs(slot.getStartTs());
        booking.setEndTs(slot.getEndTs());
        booking.setScheduledBy(CancellationActor.USER);
        booking.setLastStatusChangedAt(OffsetDateTime.now(ZoneOffset.UTC));
        booking.setPaymentMode(paymentMode);

        if (paymentMode == PaymentMode.OFFLINE) {
            booking.setStatus(BookingStatus.CONFIRMED);
        } else {
            booking.setStatus(BookingStatus.PENDING_PAYMENT);
        }

        Booking saved = bookingRepository.save(booking);

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

        BookingCreateResponse response = buildResponse(saved, totalCents);

        if (paymentMode == PaymentMode.ONLINE) {
            response.setRazorpay(paymentService.initiateRazorpayPayment(saved.getId()));
        }

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
    @Transactional
    public BookingCancelResponse cancelBooking(Long bookingId, Long userId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        Booking booking = bookingRepository.findByIdAndUserId(bookingId, userId)
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

        boolean refundInitiated = false;
        Integer refundAmountCents = null;

        if (booking.getPaymentMode() == PaymentMode.ONLINE) {
            refundAmountCents = processRefundIfNeeded(booking, now);
            refundInitiated = refundAmountCents != null && refundAmountCents > 0;
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(now);
        booking.setCancellationReasonText("User cancelled booking");
        booking.setCancelledBy(CancellationActor.USER);
        booking.setLastStatusChangedAt(now);
        bookingRepository.save(booking);

        notificationService.notifyCustomerBookingCancelled(booking);
        notificationService.notifySpaOwnerBookingCancelled(booking);

        return BookingCancelResponse.builder()
                .bookingId(booking.getId())
                .bookingReference(booking.getBookingReference())
                .status(booking.getStatus())
                .refundInitiated(refundInitiated)
                .refundAmountCents(refundAmountCents)
                .refundStatus(booking.getRefundStatus())
                .build();
    }

    private Integer processRefundIfNeeded(Booking booking, OffsetDateTime now) {
        return paymentRepository
                .findFirstByBookingIdAndPaymentStatusIn(
                        booking.getId(),
                        List.of(PaymentStatus.COMPLETED, PaymentStatus.CAPTURED))
                .map(payment -> {
                    refundService.processRefund(payment, booking);
                    payment.setPaymentStatus(PaymentStatus.REFUNDED);
                    payment.setRefundedCents(payment.getAmountCents());
                    payment.setRefundedAt(now);
                    paymentRepository.save(payment);
                    booking.setRefundStatus(RefundStatus.COMPLETED);
                    bookingRepository.save(booking);
                    return payment.getAmountCents();
                })
                .orElse(null);
    }

    private BookingSummaryResponse toSummaryResponse(Booking booking, CancellationEligibility eligibility) {
        return BookingSummaryResponse.builder()
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
                .canCancel(eligibility.canCancel())
                .cancellationReason(eligibility.reason())
                .build();
    }

    private void validateSlot(Spa spa, com.nirvana.application.model.Service service, Slot slot, int guests) {
        if (!slot.getSpa().getId().equals(spa.getId())) {
            throw new IllegalArgumentException("Slot does not belong to Spa");
        }
        if (!slot.getService().getId().equals(service.getId())) {
            throw new IllegalArgumentException("Slot does not belong to Service");
        }
        if (slot.getStartTs().isBefore(OffsetDateTime.now())) {
            throw new IllegalStateException("Cannot book a slot in the past");
        }
        if (Boolean.TRUE.equals(slot.getIsBlocked())) {
            throw new IllegalStateException("Slot is blocked");
        }
        if (slot.getStatus() != SlotStatus.OPEN) {
            throw new IllegalStateException("Slot is not open for booking");
        }
        int remaining = slot.getCapacityUnit() - slot.getBookedUnits();
        if (remaining < guests) {
            throw new IllegalStateException("Not enough capacity left in slot");
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
        response.setBookingId(booking.getId());
        response.setBookingReference(booking.getBookingReference());
        response.setStatus(booking.getStatus().name());
        response.setPaymentMode(booking.getPaymentMode() != null ? booking.getPaymentMode().name() : null);
        response.setPriceCents(booking.getPriceCents());
        response.setTaxCents(booking.getTaxCents());
        response.setDiscountCents(booking.getDiscountCents());
        response.setTotalCents(totalCents);
        response.setCurrency(booking.getCurrency());
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
