package com.nirvana.application.service.impl;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.Slot;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.User;
import com.nirvana.application.model.dto.BookingCreateRequest;
import com.nirvana.application.model.dto.BookingCreateResponse;
import com.nirvana.application.model.dto.PaymentInitResponse;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.CancellationActor;
import com.nirvana.application.model.enums.PaymentMode;
import com.nirvana.application.model.enums.RefundStatus;
import com.nirvana.application.model.enums.SlotStatus;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.SlotRepository;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.repository.UserRepository;
import com.nirvana.application.repository.ServiceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final SpaRepository spaRepository;
    private final ServiceRepository serviceRepository;
    private final SlotRepository slotRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final PaymentService paymentService;

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
        int discountCents = 0; // placeholder for coupon/discount engine
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

        // reserve capacity immediately to prevent overbooking even while payment is pending
        short updatedUnits = (short) (slot.getBookedUnits() + guests);
        slot.setBookedUnits(updatedUnits);
        if (updatedUnits >= slot.getCapacityUnit()) {
            slot.setStatus(SlotStatus.BOOKED);
        }
        slotRepository.save(slot);

        BookingCreateResponse response = buildResponse(saved, totalCents);

        if (paymentMode == PaymentMode.ONLINE) {
            PaymentInitResponse paymentInitResponse = paymentService.initiateRazorpayPayment(saved.getId());
            response.setRazorpay(paymentInitResponse);
        }

        return response;
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
