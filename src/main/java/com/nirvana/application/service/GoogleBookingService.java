package com.nirvana.application.service;

import com.nirvana.application.model.Address;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.dto.GoogleBookingConfigResponse;
import com.nirvana.application.model.dto.GoogleBookingSpaSummary;
import com.nirvana.application.model.dto.GooglePlaceLinkRequest;
import com.nirvana.application.model.dto.GooglePlaceLinkResponse;
import com.nirvana.application.model.dto.ServiceSummaryResponse;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.service.impl.SpaReadService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoogleBookingService {

    private final SpaRepository spaRepository;
    private final SpaReadService spaReadService;

    public GoogleBookingConfigResponse resolveSpaByPlaceId(String placeId, Long fallbackSpaId) {
        if (!StringUtils.hasText(placeId) && fallbackSpaId == null) {
            throw new IllegalArgumentException("placeId is required");
        }

        Spa spa = findSpa(placeId, fallbackSpaId);
        List<ServiceSummaryResponse> services = spaReadService.getSpaServices(spa.getId());

        return GoogleBookingConfigResponse.builder()
                .spa(toSummary(spa))
                .services(services)
                .therapistSelectionEnabled(Boolean.TRUE.equals(spa.getAllowTherapistSelection()))
                .therapistTypeSelectionEnabled(Boolean.TRUE.equals(spa.getAllowTherapistTypeSelection()))
                .build();
    }

    @Transactional
    public GooglePlaceLinkResponse linkGooglePlace(Long spaId, GooglePlaceLinkRequest request) {
        if (request == null || !StringUtils.hasText(request.getGooglePlaceId())) {
            throw new IllegalArgumentException("googlePlaceId is required");
        }
        String trimmedPlaceId = request.getGooglePlaceId().trim();

        Spa spa = spaRepository.findById(spaId)
                .orElseThrow(() -> new EntityNotFoundException("Spa not found: " + spaId));

        spaRepository.findByGooglePlaceId(trimmedPlaceId)
                .filter(existing -> !existing.getId().equals(spaId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Google Place ID already linked to another spa");
                });

        spa.setGooglePlaceId(trimmedPlaceId);
        spa.setGoogleMapsUrl(StringUtils.hasText(request.getGoogleMapsUrl()) ? request.getGoogleMapsUrl().trim() : null);
        if (spa.getAddress() != null && !StringUtils.hasText(spa.getAddress().getGooglePlaceId())) {
            spa.getAddress().setGooglePlaceId(trimmedPlaceId);
        }

        Spa saved = spaRepository.save(spa);
        return GooglePlaceLinkResponse.builder()
                .spaId(saved.getId())
                .spaName(saved.getName())
                .googlePlaceId(saved.getGooglePlaceId())
                .googleMapsUrl(saved.getGoogleMapsUrl())
                .build();
    }

    private Spa findSpa(String placeId, Long fallbackSpaId) {
        Optional<Spa> spaOpt = Optional.empty();
        if (StringUtils.hasText(placeId)) {
            String normalized = placeId.trim();
            spaOpt = spaRepository.findByGooglePlaceId(normalized)
                    .or(() -> spaRepository.findByAddress_GooglePlaceId(normalized));
        }
        if (spaOpt.isEmpty() && fallbackSpaId != null) {
            spaOpt = spaRepository.findById(fallbackSpaId);
        }

        Spa spa = spaOpt.orElseThrow(() -> new EntityNotFoundException("SPA_NOT_ONBOARDED"));
        if (Boolean.FALSE.equals(spa.getIsActive()) || Boolean.FALSE.equals(spa.getIsVerified())) {
            throw new IllegalStateException("Spa is not available for booking");
        }
        return spa;
    }

    private GoogleBookingSpaSummary toSummary(Spa spa) {
        Address address = spa.getAddress();
        String googlePlaceId = spa.getGooglePlaceId() != null
                ? spa.getGooglePlaceId()
                : address != null ? address.getGooglePlaceId() : null;

        return GoogleBookingSpaSummary.builder()
                .spaId(spa.getId())
                .spaName(spa.getName())
                .googlePlaceId(googlePlaceId)
                .googleMapsUrl(spa.getGoogleMapsUrl())
                .formattedAddress(address != null ? address.getFormattedAddress() : null)
                .city(address != null ? address.getCity() : null)
                .state(address != null ? address.getState() : null)
                .country(address != null ? address.getCountry() : null)
                .timezone(address != null && StringUtils.hasText(address.getTimezone()) ? address.getTimezone() : spa.getTimezone())
                .defaultCurrency(spa.getDefaultCurrency())
                .active(spa.getIsActive())
                .verified(spa.getIsVerified())
                .displayUrl("/spa/" + spa.getId())
                .build();
    }
}
