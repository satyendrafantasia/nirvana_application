package com.nirvana.application.service.impl;

// src/main/java/com/nirvana/application/service/SpaReadService.java

import com.nirvana.application.model.Address;
import com.nirvana.application.model.MediaAsset;
import com.nirvana.application.model.Service;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.Therapist;
import com.nirvana.application.model.dto.ServiceSummaryResponse;
import com.nirvana.application.model.dto.SpaDetailResponse;
import com.nirvana.application.model.enums.MediaType;
import com.nirvana.application.model.enums.spa.TherapistType;
import com.nirvana.application.repository.MediaAssetRepository;
import com.nirvana.application.repository.ServiceRepository;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.service.S3Service;
import com.nirvana.application.utils.JsonUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@org.springframework.stereotype.Service
@Slf4j
@RequiredArgsConstructor
public class SpaReadService {

    private final SpaRepository spaRepository;
    private final ServiceRepository serviceRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final S3Service s3Service;

    @Transactional(readOnly = true)
    public SpaDetailResponse getSpaDetails(Long spaId) {
        Spa spa = spaRepository.findById(spaId)
                .orElseThrow(() -> new EntityNotFoundException("Spa not found: " + spaId));

        Address a = spa.getAddress();

        List<MediaAsset> mediaAssets = spa.getMediaAssets() != null
                ? spa.getMediaAssets().stream()
                .sorted(this::compareMediaAssets)
                .toList()
                : Collections.emptyList();

        List<String> imageUrls = mediaAssets.stream()
                .filter(asset -> asset.getMediaType() == MediaType.IMAGE)
                .map(this::toS3Url)
                .toList();

        List<String> videoUrls = mediaAssets.stream()
                .filter(asset -> asset.getMediaType() == MediaType.VIDEO)
                .map(this::toS3Url)
                .toList();

        String heroImageUrl = !imageUrls.isEmpty()
                ? imageUrls.get(0)
                : mediaAssetRepository
                        .findFirstBySpaIdAndMediaTypeOrderByPositionAscIdAsc(spa.getId(), MediaType.IMAGE)
                        .map(media -> s3Service.getFileUrl(media.getObjectKey()))
                        .orElse(null);

        List<String> images = JsonUtils.toStringList(spa.getImagesJson());
        List<String> amenities = arrayToList(spa.getAmenities());
        List<String> tags = arrayToList(spa.getTags());

        List<Long> therapistIds = spa.getTherapists() != null
                ? spa.getTherapists().stream().map(Therapist::getId).toList()
                : List.of();

        List<Long> serviceIds = spa.getServices() != null
                ? spa.getServices().stream().map(Service::getId).toList()
                : List.of();

        List<String> finalImages = !imageUrls.isEmpty() ? imageUrls : images;

        return new SpaDetailResponse(
                spa.getId(),
                spa.getName(),
                spa.getDescription(),

                a != null ? a.getAddressLine() : null,
                a != null ? a.getAddressLine2() : null,
                a != null ? a.getLocality() : null,
                a != null ? a.getLandmark() : null,
                a != null ? a.getCity() : null,
                a != null ? a.getState() : null,
                a != null ? a.getPostalCode() : null,
                a != null ? a.getCountry() : null,
                a != null ? a.getCountryCode() : null,
                a != null ? a.getFormattedAddress() : null,
                a != null ? a.getTimezone() : spa.getTimezone(),
                a != null ? a.getGooglePlaceId() : null,
                toDouble(a != null ? a.getLatitude() : null),
                toDouble(a != null ? a.getLongitude() : null),

                spa.getPhone(),
                spa.getEmail(),
                spa.getWebsiteUrl(),
                spa.getFacebookUrl(),
                spa.getInstagramUrl(),

                spa.getGstin(),
                spa.getBusinessRegistrationNumber(),
                spa.getOwnerName(),
                spa.getKycStatus(),
                spa.getKycRequestedAt(),
                spa.getKycApprovedAt(),
                spa.getKycRejectedAt(),
                spa.getKycRejectedReason(),
                spa.getIsActive(),
                spa.getIsVerified(),
                spa.getIsFeatured(),

                spa.getRatingAvg(),
                spa.getRatingCount(),
                spa.getTotalBookings(),

                spa.getDefaultCurrency(),
                spa.getTaxPercent(),
                spa.getCommissionPct(),
                spa.getMaxConcurrentServices(),
                spa.getMaxAdvanceBookingDays(),
                spa.getMinNoticeMinutes(),

                formatTime(spa.getOpenTimeLocal()),
                formatTime(spa.getCloseTimeLocal()),
                spa.getWorkingDays() != null ? spa.getWorkingDays().stream().sorted().toList() : List.of(),

                spa.getAllowTherapistSelection(),
                spa.getAllowTherapistTypeSelection(),
                safeSet(spa.getTherapistTypesAvailable()),
                therapistIds,

                serviceIds,

                finalImages,
                videoUrls,
                amenities,
                tags,

                heroImageUrl
        );
    }

    @Transactional(readOnly = true)
    public List<ServiceSummaryResponse> getSpaServices(Long spaId) {
        // Ensure spa exists & is active
        Spa spa = spaRepository.findById(spaId)
                .orElseThrow(() -> new EntityNotFoundException("Spa not found: " + spaId));
        if (Boolean.FALSE.equals(spa.getIsActive())) {
            throw new EntityNotFoundException("Spa is inactive: " + spaId);
        }

        List<Service> services = serviceRepository.findBySpaIdAndIsActiveTrueOrderByNameAsc(spaId);

        return services.stream()
                .map(this::toServiceSummary)
                .toList();
    }

    private ServiceSummaryResponse toServiceSummary(Service s) {
        return new ServiceSummaryResponse(
                s.getId(),
                s.getServiceCode(),
                s.getName(),
                s.getDescription(),
                s.getCategory(),
                s.getSubCategory(),
                s.getDurationMinutes() != null ? s.getDurationMinutes() : s.getDurationMin(),
                s.getBufferMin(),
                s.getMinPersons(),
                s.getMaxPersons(),
                s.getCurrency(),
                s.getBasePriceCents(),
                s.getPriceCents(),
                s.getIsActive(),
                s.getIsVisibleOnMarketplace(),
                s.getGenderAllowed()
        );
    }

    private static Double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }

    private static List<String> arrayToList(String[] arr) {
        return (arr == null || arr.length == 0) ? List.of() : Arrays.asList(arr);
    }

    private int compareMediaAssets(MediaAsset a, MediaAsset b) {
        Integer posA = a.getPosition();
        Integer posB = b.getPosition();
        if (Objects.equals(posA, posB)) {
            Long idA = a.getId();
            Long idB = b.getId();
            if (idA == null || idB == null) {
                return 0;
            }
            return idA.compareTo(idB);
        }
        if (posA == null) return 1;
        if (posB == null) return -1;
        return posA.compareTo(posB);
    }

    private String toS3Url(MediaAsset asset) {
        return s3Service.getFileUrl(asset.getObjectKey());
    }

    private String formatTime(LocalTime time) {
        return time != null ? time.toString() : null;
    }

    private Set<TherapistType> safeSet(Set<TherapistType> source) {
        return source != null ? source : Set.of();
    }
}
