package com.nirvana.application.service.impl;

import com.nirvana.application.model.Slot;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.WaitlistEntry;
import com.nirvana.application.model.dto.WaitlistRequest;
import com.nirvana.application.model.dto.WaitlistResponse;
import com.nirvana.application.repository.BookingHoldRepository;
import com.nirvana.application.repository.ServiceRepository;
import com.nirvana.application.repository.SlotRepository;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.repository.WaitlistRepository;
import com.nirvana.application.repository.UserRepository;
import com.nirvana.application.service.NotificationService;
import com.nirvana.application.service.WaitlistService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WaitlistServiceImpl implements WaitlistService {

    private final WaitlistRepository waitlistRepository;
    private final SpaRepository spaRepository;
    private final ServiceRepository serviceRepository;
    private final SlotRepository slotRepository;
    private final BookingHoldRepository bookingHoldRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public WaitlistResponse joinWaitlist(Long userId, WaitlistRequest request) {
        Spa spa = spaRepository.findById(request.getSpaId())
                .orElseThrow(() -> new EntityNotFoundException("Spa not found: " + request.getSpaId()));
        com.nirvana.application.model.Service service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new EntityNotFoundException("Service not found: " + request.getServiceId()));
        Slot slot = slotRepository.findById(request.getSlotId())
                .orElseThrow(() -> new EntityNotFoundException("Slot not found: " + request.getSlotId()));

        if (!slot.getSpa().getId().equals(spa.getId())) {
            throw new IllegalArgumentException("Slot does not belong to spa");
        }
        if (!slot.getService().getId().equals(service.getId())) {
            throw new IllegalArgumentException("Slot does not belong to service");
        }

        if (userId != null) {
            Optional<WaitlistEntry> existing = waitlistRepository.findBySlotIdAndUserId(slot.getId(), userId);
            if (existing.isPresent() && Boolean.TRUE.equals(existing.get().getActive())) {
                return toResponse(existing.get());
            }
        }

        WaitlistEntry entry = WaitlistEntry.builder()
                .user(userId != null ? userRepository.findById(userId).orElse(null) : null)
                .spa(spa)
                .service(service)
                .slot(slot)
                .guests(Optional.ofNullable(request.getGuests()).orElse(1))
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .active(true)
                .notified(false)
                .build();

        WaitlistEntry saved = waitlistRepository.save(entry);
        tryNotifySlotAvailable(slot);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void tryNotifySlotAvailable(Slot slot) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int holdUnits = Optional.ofNullable(bookingHoldRepository.findActiveHoldUnits(slot.getId(), now)).orElse(0);
        int remaining = slot.getCapacityUnit() - slot.getBookedUnits() - holdUnits;
        if (remaining <= 0) {
            return;
        }

        List<WaitlistEntry> pending = waitlistRepository.findActiveForSlot(slot.getId());
        for (WaitlistEntry entry : pending) {
            if (remaining < entry.getGuests()) {
                break;
            }
            remaining -= entry.getGuests();
            entry.setNotified(true);
            entry.setNotifiedAt(now);
            entry.setActive(false);
            waitlistRepository.save(entry);
            try {
                notificationService.notifyWaitlistUserSlotAvailable(entry, slot);
            } catch (Exception e) {
                log.warn("Failed to notify waitlist entry {} for slot {}: {}", entry.getId(), slot.getId(), e.getMessage());
            }
        }
    }

    private WaitlistResponse toResponse(WaitlistEntry entry) {
        return new WaitlistResponse(entry.getId(), Boolean.TRUE.equals(entry.getNotified()), entry.getNotifiedAt());
    }
}
