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
    private final SlotAvailabilityService availability;
    private final SessionAllocation allocation;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public SlotHoldResponse holdSlot(Long userId, SlotHoldRequest request) {
        Spa spa = spaRepository.lockSchedule(request.getSpaId())
                .orElseThrow(() -> new EntityNotFoundException("Spa not found: " + request.getSpaId()));
        com.nirvana.application.model.Service service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new EntityNotFoundException("Service not found: " + request.getServiceId()));
        Slot slot = slotRepository.findByIdForUpdate(request.getSlotId())
                .orElseThrow(() -> new EntityNotFoundException("Slot not found: " + request.getSlotId()));

        bookingHoldRepository.deleteByExpiresAtBefore(OffsetDateTime.now(ZoneOffset.UTC));
        validateSlot(spa, service, slot, request.getGuests());

        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(Optional.ofNullable(request.getHoldMinutes()).orElse(10));
        BookingHold hold = new BookingHold();
        allocation.validatePreference(spa,request.getTherapistId(),request.getTherapistType());
        hold.setTherapistPreference(SessionAllocation.preference(request.getTherapistId(),request.getTherapistType()));
        hold.setRequestedLabel(request.getTherapistType());
        hold.setRoom(slot.getRoom());
        hold.setTherapist(allocation.choose(slot,request.getTherapistId(),request.getTherapistType(),null,null));
        hold.setSpa(spa);
        hold.setServicesJson(service.getId().toString());
        hold.setSlot(slot);
        hold.setUser(userId != null ? userRepository.findById(userId).orElse(null) : null);
        hold.setHoldToken(UUID.randomUUID().toString());
        hold.setExpiresAt(expiresAt);
        hold.setHoldUnits(request.getGuests());
        bookingHoldRepository.save(hold);

        var response=new SlotHoldResponse(hold.getHoldToken(), expiresAt, slot.getId());
        response.setTherapistName(SessionAllocation.customerDisplay(spa,hold.getTherapist()));
        return response;
    }

    private void validateSlot(Spa spa, com.nirvana.application.model.Service service, Slot slot, int guests) {
        if (!slot.getSpa().getId().equals(spa.getId())) {
            throw new IllegalArgumentException("Slot does not belong to Spa");
        }
        if (!slot.getService().getId().equals(service.getId())) {
            throw new IllegalArgumentException("Slot does not belong to Service");
        }
        if (!Boolean.TRUE.equals(spa.getIsActive()) || !Boolean.TRUE.equals(spa.getIsVerified()) ||
                !Boolean.TRUE.equals(service.getIsActive()) || guests<service.getMinPersons() || guests>service.getMaxPersons())
            throw new IllegalStateException("Spa, service or guest count is unavailable");
        int held=bookingHoldRepository.findActiveHoldUnits(slot.getId(),OffsetDateTime.now());
        availability.requireBookable(slot,guests,held);

    }
}
