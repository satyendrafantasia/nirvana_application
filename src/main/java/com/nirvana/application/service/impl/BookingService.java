package com.nirvana.application.service.impl;

// src/main/java/com/nirvana/application/service/BookingService.java

import com.nirvana.application.model.*;
import com.nirvana.application.model.dto.BookingResponse;
import com.nirvana.application.model.dto.CreateBookingRequest;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.CancellationActor;
import com.nirvana.application.model.enums.ScheduledBy;
import com.nirvana.application.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final SpaRepository spaRepository;
    private final ServiceRepository serviceRepository;
    private final SlotRepository slotRepository;
    private final UserRepository appUserRepository;
    private final BookingRepository bookingRepository;

    @Transactional
    public BookingResponse createBooking(
            Long spaId,
            Long serviceId,
            Long slotId,
            Long userId,
            CreateBookingRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Request must not be null");
        }
        int guestCount = request.guestCount() != null ? request.guestCount() : 1;
        if (guestCount <= 0) {
            throw new IllegalArgumentException("guestCount must be > 0");
        }

        Spa spa = spaRepository.findById(spaId)
                .orElseThrow(() -> new EntityNotFoundException("Spa not found: " + spaId));

        com.nirvana.application.model.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new EntityNotFoundException("Service not found: " + serviceId));

        if (!service.getSpa().getId().equals(spa.getId())) {
            throw new IllegalArgumentException("Service does not belong to Spa");
        }

        User user = appUserRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        // Lock slot row to prevent race conditions
        Slot slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new EntityNotFoundException("Slot not found: " + slotId));

        if (!slot.getSpa().getId().equals(spa.getId())) {
            throw new IllegalArgumentException("Slot does not belong to Spa");
        }
        if (!slot.getService().getId().equals(service.getId())) {
            throw new IllegalArgumentException("Slot does not belong to Service");
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (slot.getStartTs().isBefore(now)) {
            throw new IllegalStateException("Cannot book a slot in the past");
        }

        if (Boolean.TRUE.equals(slot.getIsBlocked())) {
            throw new IllegalStateException("Slot is blocked");
        }

        if (slot.getStatus() != com.nirvana.application.model.enums.SlotStatus.OPEN) {
            throw new IllegalStateException("Slot is not open for booking");
        }

        short capacity = slot.getCapacityUnit();
        short booked = slot.getBookedUnits();
        int remaining = capacity - booked;
        if (remaining < guestCount) {
            throw new IllegalStateException("Not enough capacity left in slot");
        }

        // pricing
        String currency = service.getCurrency() != null ? service.getCurrency() : spa.getDefaultCurrency();
        int unitPrice = service.getPriceCents() != null
                ? service.getPriceCents()
                : (service.getBasePriceCents() != null ? service.getBasePriceCents() : 0);

        int priceCents = unitPrice * guestCount;
        int discountCents = 0;
        int taxCents = 0;
        int depositCents = 0;

        // TODO: add pricing engine / tax engine later

        String bookingRef = generateUniqueBookingReference();

        Booking booking = new Booking();
        booking.setSpa(spa);
        booking.setService(service);
        booking.setSlot(slot);
        booking.setUser(user);

        booking.setBookingReference(bookingRef);
        booking.setCurrency(currency);
        booking.setGuestCount(guestCount);
        booking.setPriceCents(priceCents);
        booking.setDiscountCents(discountCents);
        booking.setTaxCents(taxCents);
        booking.setDepositCents(depositCents);
        booking.setRemainderCents(priceCents + taxCents - discountCents - depositCents);

        booking.setCustomerNotes(request.customerNotes());
        booking.setIsTestBooking(Boolean.TRUE.equals(request.testBooking()));
        booking.setCouponCode(request.couponCode());
        booking.setIpAddress(request.clientIp());

        booking.setStartTs(slot.getStartTs());
        booking.setEndTs(slot.getEndTs());

        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setScheduledBy(CancellationActor.USER);
        booking.setRefundStatus(com.nirvana.application.model.enums.RefundStatus.NONE);

        booking.setLastStatusChangedAt(now);

        Booking saved = bookingRepository.save(booking);

        // update slot usage
        slot.setBookedUnits((short) (booked + guestCount));
        if (slot.getBookedUnits() >= capacity) {
            slot.setStatus(com.nirvana.application.model.enums.SlotStatus.BOOKED);
        }
        slotRepository.save(slot);

        return toResponse(saved);
    }

    private String generateUniqueBookingReference() {
        while (true) {
            String ref = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
            if (!bookingRepository.existsByBookingReference(ref)) {
                return ref;
            }
        }
    }

    private BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getBookingReference(),
                booking.getSpa().getId(),
                booking.getService().getId(),
                booking.getSlot().getId(),
                booking.getUser().getId(),
                booking.getStatus(),
                booking.getGuestCount(),
                booking.getPriceCents(),
                booking.getTaxCents(),
                booking.getDiscountCents(),
                booking.getDepositCents(),
                booking.getCurrency(),
                booking.getStartTs(),
                booking.getEndTs()
        );
    }
}
