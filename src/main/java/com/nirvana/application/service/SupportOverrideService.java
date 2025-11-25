package com.nirvana.application.service;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.SupportOverride;
import com.nirvana.application.model.dto.SupportOverrideRequest;
import com.nirvana.application.model.dto.SupportOverrideResolutionRequest;
import com.nirvana.application.model.dto.SupportOverrideResponse;
import com.nirvana.application.model.enums.SupportOverrideStatus;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.SupportOverrideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupportOverrideService {

    private final SupportOverrideRepository supportOverrideRepository;
    private final BookingRepository bookingRepository;
    private final ProviderAuditService providerAuditService;

    @Transactional
    public SupportOverrideResponse requestOverride(SupportOverrideRequest request) {
        SupportOverride override = new SupportOverride();
        if (request.getSpaId() != null) {
            override.setSpa(new com.nirvana.application.model.Spa());
            override.getSpa().setId(request.getSpaId());
        }
        Booking booking = null;
        if (request.getBookingId() != null) {
            booking = bookingRepository.findById(request.getBookingId())
                    .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
        }
        override.setBooking(booking);
        override.setOverrideType(request.getOverrideType());
        override.setPayload(request.getPayload());
        override.setRequestedBy(request.getRequestedBy());
        override.setStatus(SupportOverrideStatus.REQUESTED);

        SupportOverride saved = supportOverrideRepository.save(override);
        providerAuditService.log(request.getSpaId(), "SUPPORT", null, "OVERRIDE_REQUESTED", request.getOverrideType());
        return toResponse(saved);
    }

    @Transactional
    public SupportOverrideResponse resolve(Long overrideId, SupportOverrideResolutionRequest request) {
        SupportOverride override = supportOverrideRepository.findById(overrideId)
                .orElseThrow(() -> new IllegalArgumentException("Override not found"));
        override.setStatus(request.getStatus());
        override.setResolvedBy(request.getResolvedBy());
        override.setResolutionNotes(request.getResolutionNotes());
        override.setResolvedAt(OffsetDateTime.now());

        SupportOverride saved = supportOverrideRepository.save(override);
        Long spaId = saved.getSpa() != null ? saved.getSpa().getId() : null;
        providerAuditService.log(spaId, "SUPPORT", null, "OVERRIDE_" + request.getStatus().name(), request.getResolutionNotes());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SupportOverrideResponse> listForSpa(Long spaId) {
        return supportOverrideRepository.findBySpa_Id(spaId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private SupportOverrideResponse toResponse(SupportOverride entity) {
        SupportOverrideResponse response = new SupportOverrideResponse();
        response.setId(entity.getId());
        response.setSpaId(entity.getSpa() != null ? entity.getSpa().getId() : null);
        response.setBookingId(entity.getBooking() != null ? entity.getBooking().getId() : null);
        response.setOverrideType(entity.getOverrideType());
        response.setStatus(entity.getStatus());
        response.setPayload(entity.getPayload());
        response.setRequestedBy(entity.getRequestedBy());
        response.setResolvedBy(entity.getResolvedBy());
        response.setResolvedAt(entity.getResolvedAt());
        response.setResolutionNotes(entity.getResolutionNotes());
        return response;
    }
}
