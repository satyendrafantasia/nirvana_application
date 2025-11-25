package com.nirvana.application.service.impl;

import com.nirvana.application.model.BookingHold;
import com.nirvana.application.model.Slot;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.User;
import com.nirvana.application.model.dto.SlotHoldRequest;
import com.nirvana.application.model.dto.SlotHoldResponse;
import com.nirvana.application.model.enums.SlotStatus;
import com.nirvana.application.repository.BookingHoldRepository;
import com.nirvana.application.repository.ServiceRepository;
import com.nirvana.application.repository.SlotRepository;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.repository.UserRepository;
import com.nirvana.application.service.BookingHoldService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingHoldServiceImpl implements BookingHoldService {

    private final BookingHoldRepository bookingHoldRepository;
    private final SlotRepository slotRepository;
    private final SpaRepository spaRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public SlotHoldResponse holdSlot(Long userId, SlotHoldRequest request) {
        bookingHoldRepository.deleteByExpiresAtBefore(OffsetDateTime.now(ZoneOffset.UTC));

        Spa spa = spaRepository.findById(request.getSpaId())
                .orElseThrow(() -> new EntityNotFoundException("Spa not found: " + request.getSpaId()));
        com.nirvana.application.model.Service service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new EntityNotFoundException("Service not found: " + request.getServiceId()));
        Slot slot = slotRepository.findByIdForUpdate(request.getSlotId())
                .orElseThrow(() -> new EntityNotFoundException("Slot not found: " + request.getSlotId()));

        validateSlot(spa, service, slot, request.getGuests());

        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(Optional.ofNullable(request.getHoldMinutes()).orElse(10));
        BookingHold hold = new BookingHold();
        hold.setSpa(spa);
        hold.setServicesJson(service.getId().toString());
        hold.setSlot(slot);
        hold.setUser(userId != null ? userRepository.findById(userId).orElse(null) : null);
        hold.setHoldToken(UUID.randomUUID().toString());
        hold.setExpiresAt(expiresAt);
        hold.setHoldUnits(request.getGuests());
        bookingHoldRepository.save(hold);

        return new SlotHoldResponse(hold.getHoldToken(), expiresAt, slot.getId());
    }

    private void validateSlot(Spa spa, com.nirvana.application.model.Service service, Slot slot, int guests) {
        if (!slot.getSpa().getId().equals(spa.getId())) {
            throw new IllegalArgumentException("Slot does not belong to Spa");
        }
        if (!slot.getService().getId().equals(service.getId())) {
            throw new IllegalArgumentException("Slot does not belong to Service");
        }
        if (slot.getStartTs().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new IllegalStateException("Cannot hold a slot in the past");
        }
        if (Boolean.TRUE.equals(slot.getIsBlocked()) || slot.getStatus() != SlotStatus.OPEN) {
            throw new IllegalStateException("Slot is not open for holds");
        }
        int activeHolds = Optional.ofNullable(bookingHoldRepository.findActiveHoldUnits(slot.getId(), OffsetDateTime.now(ZoneOffset.UTC))).orElse(0);
        int remaining = slot.getCapacityUnit() - slot.getBookedUnits() - activeHolds;
        if (remaining < guests) {
            throw new IllegalStateException("Not enough capacity to hold");
        }
    }
}
