package com.nirvana.application.service;

import com.nirvana.application.model.Address;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.dto.GooglePlaceDetailsResponse;
import com.nirvana.application.model.dto.SpaOnboardingSummaryResponse;
import com.nirvana.application.model.enums.KycStatus;
import com.nirvana.application.repository.SpaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GooglePlaceOnboardingService {

    private final GooglePlacesClient googlePlacesClient;
    private final SpaRepository spaRepository;

    @Transactional
    public SpaOnboardingSummaryResponse onboardFromPlaceId(String placeId) {
        if (!StringUtils.hasText(placeId)) {
            throw new IllegalArgumentException("placeId is required");
        }

        Optional<Spa> existing = spaRepository.findByAddress_GooglePlaceId(placeId);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Spa with this Google Place ID already exists");
        }

        GooglePlaceDetailsResponse response = googlePlacesClient.fetchPlaceDetails(placeId);
        if (response == null || response.getResult() == null || !"OK".equalsIgnoreCase(response.getStatus())) {
            throw new IllegalStateException("Failed to fetch place details from Google Places");
        }

        Spa spa = mapToSpa(response.getResult());
        Spa saved = spaRepository.save(spa);
        return toSummary(saved);
    }

    @Transactional
    public SpaOnboardingSummaryResponse approveImportedSpa(Long spaId) {
        Spa spa = spaRepository.findById(spaId)
                .orElseThrow(() -> new EntityNotFoundException("Spa not found: " + spaId));

        if (spa.getAddress() == null || !StringUtils.hasText(spa.getAddress().getGooglePlaceId())) {
            throw new IllegalArgumentException("Spa is not linked to a Google Place");
        }

        spa.setIsActive(true);
        spa.setIsVerified(true);
        spa.setKycStatus(KycStatus.APPROVED);
        spa.setKycApprovedAt(OffsetDateTime.now(ZoneOffset.UTC));

        Spa saved = spaRepository.save(spa);
        return toSummary(saved);
    }

    private Spa mapToSpa(GooglePlaceDetailsResponse.Result result) {
        Spa spa = new Spa();
        spa.setName(result.getName());
        spa.setPhone(result.getInternationalPhoneNumber());
        spa.setWebsiteUrl(result.getWebsite());

        spa.setIsActive(false);
        spa.setIsVerified(false);
        spa.setIsFeatured(false);
        spa.setKycStatus(KycStatus.PENDING);

        spa.setAddress(buildAddress(result));
        if (spa.getAddress() != null && spa.getAddress().getTimezone() != null) {
            spa.setTimezone(spa.getAddress().getTimezone());
        }
        return spa;
    }

    private Address buildAddress(GooglePlaceDetailsResponse.Result result) {
        Address address = new Address();
        address.setGooglePlaceId(result.getPlaceId());
        address.setFormattedAddress(result.getFormattedAddress());
        address.setGeoSource("GOOGLE_PLACES");
        address.setAddressType("SPA_BRANCH");

        String formattedAddress = Optional.ofNullable(result.getFormattedAddress())
                .filter(StringUtils::hasText)
                .orElse(result.getName());
        address.setAddressLine(formattedAddress);
        address.setCity(resolveComponent(result.getAddressComponents(), List.of("locality", "administrative_area_level_2", "administrative_area_level_1"), "Unknown City"));
        address.setState(resolveComponent(result.getAddressComponents(), List.of("administrative_area_level_1"), null));
        address.setPostalCode(resolveComponent(result.getAddressComponents(), List.of("postal_code"), null));
        address.setCountry(resolveComponent(result.getAddressComponents(), List.of("country"), null));
        address.setCountryCode(resolveComponentShort(result.getAddressComponents(), List.of("country"), null));
        address.setLocality(resolveComponent(result.getAddressComponents(), List.of("sublocality", "neighborhood"), null));

        if (result.getGeometry() != null && result.getGeometry().getLocation() != null) {
            address.setLatitude(result.getGeometry().getLocation().getLat());
            address.setLongitude(result.getGeometry().getLocation().getLng());
        }

        return address;
    }

    private String resolveComponent(List<GooglePlaceDetailsResponse.AddressComponent> components,
                                    List<String> typePriority,
                                    String defaultValue) {
        if (CollectionUtils.isEmpty(components)) {
            return defaultValue;
        }

        return components.stream()
                .filter(component -> component.getTypes() != null && !component.getTypes().isEmpty())
                .sorted(Comparator.comparingInt(c -> rankComponent(c.getTypes(), typePriority)))
                .filter(component -> rankComponent(component.getTypes(), typePriority) < Integer.MAX_VALUE)
                .findFirst()
                .map(GooglePlaceDetailsResponse.AddressComponent::getLongName)
                .orElse(defaultValue);
    }

    private String resolveComponentShort(List<GooglePlaceDetailsResponse.AddressComponent> components,
                                         List<String> typePriority,
                                         String defaultValue) {
        if (CollectionUtils.isEmpty(components)) {
            return defaultValue;
        }

        return components.stream()
                .filter(component -> component.getTypes() != null && !component.getTypes().isEmpty())
                .sorted(Comparator.comparingInt(c -> rankComponent(c.getTypes(), typePriority)))
                .filter(component -> rankComponent(component.getTypes(), typePriority) < Integer.MAX_VALUE)
                .findFirst()
                .map(GooglePlaceDetailsResponse.AddressComponent::getShortName)
                .orElse(defaultValue);
    }

    private int rankComponent(List<String> types, List<String> priority) {
        for (int i = 0; i < priority.size(); i++) {
            if (types.contains(priority.get(i))) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private SpaOnboardingSummaryResponse toSummary(Spa spa) {
        Address a = spa.getAddress();
        return SpaOnboardingSummaryResponse.builder()
                .id(spa.getId())
                .name(spa.getName())
                .city(a != null ? a.getCity() : null)
                .countryCode(a != null ? a.getCountryCode() : null)
                .active(Boolean.TRUE.equals(spa.getIsActive()))
                .verified(Boolean.TRUE.equals(spa.getIsVerified()))
                .featured(Boolean.TRUE.equals(spa.getIsFeatured()))
                .kycStatus(spa.getKycStatus())
                .kycRequestedAt(spa.getKycRequestedAt())
                .kycApprovedAt(spa.getKycApprovedAt())
                .kycRejectedAt(spa.getKycRejectedAt())
                .kycRejectedReason(spa.getKycRejectedReason())
                .ratingAvg(spa.getRatingAvg())
                .ratingCount(spa.getRatingCount())
                .build();
    }
}
