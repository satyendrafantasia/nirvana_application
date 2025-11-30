package com.nirvana.application.service.impl;

import com.nirvana.application.exception.BusinessException;
import com.nirvana.application.exception.NotFoundException;
import com.nirvana.application.model.Address;
import com.nirvana.application.model.MediaAsset;

import com.nirvana.application.model.Spa;
import com.nirvana.application.model.Therapist;
import com.nirvana.application.model.dto.AddressDTO;
import com.nirvana.application.model.dto.SpaMediaImageDto;
import com.nirvana.application.model.dto.SpaMediaVideoDto;
import com.nirvana.application.model.dto.SpaRequestDTO;
import com.nirvana.application.model.dto.SpaResponseDTO;
import com.nirvana.application.model.enums.MediaType;
import com.nirvana.application.model.enums.spa.TherapistType;
import com.nirvana.application.repository.ServiceRepository;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.repository.TherapistRepository;
import com.nirvana.application.service.SpaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class SpaServiceImpl implements SpaService {

    private final SpaRepository spaRepository;
    private final TherapistRepository therapistRepository;
    private final ServiceRepository serviceRepository;

    @Transactional
    @Override
    public SpaResponseDTO createSpa(SpaRequestDTO request) {
        validateBusiness(request);

        Spa spa = new Spa();
        applyRequestToEntity(spa, request, false);
        Spa saved = spaRepository.save(spa);
        attachTherapists(saved, request.getTherapistIds());
        attachServices(saved, request.getServiceIds());
        attachMediaAssets(saved, request.getImages(), request.getVideos());
        Spa persisted = spaRepository.save(saved);
        return toResponseDto(persisted);
    }

    @Transactional(readOnly = true)
    @Override
    public SpaResponseDTO getSpaById(Long id) {
        Spa spa = spaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Spa not found with id: " + id));
        return toResponseDto(spa);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<SpaResponseDTO> listSpas(Pageable pageable) {
        return spaRepository.findAll(pageable)
                .map(this::toResponseDto);
    }

    @Transactional
    @Override
    public SpaResponseDTO updateSpa(Long id, SpaRequestDTO request) {
        validateBusiness(request);

        Spa spa = spaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Spa not found with id: " + id));

        // optimistic locking: version must match
        if (request.getVersion() == null) {
            throw new BusinessException("Version is required for update");
        }
        if (!request.getVersion().equals(spa.getVersion())) {
            throw new BusinessException("Version mismatch: concurrent modification detected");
        }

        applyRequestToEntity(spa, request, true);
        attachTherapists(spa, request.getTherapistIds());
        attachServices(spa, request.getServiceIds());
        attachMediaAssets(spa, request.getImages(), request.getVideos());
        Spa saved = spaRepository.save(spa);
        return toResponseDto(saved);
    }

    @Transactional
    @Override
    public void deactivateSpa(Long id) {
        Spa spa = spaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Spa not found with id: " + id));
        spa.setIsActive(false);
        spaRepository.save(spa);
    }

    // ---------- Business validation ----------

    private void validateBusiness(SpaRequestDTO req) {
        // Validate timezone
        try {
            ZoneId.of(req.getTimezone());
        } catch (Exception e) {
            throw new BusinessException("Invalid timezone: " + req.getTimezone());
        }

        // Validate tax/commission range
        if (req.getTaxPercent() != null && (req.getTaxPercent() < 0 || req.getTaxPercent() > 100)) {
            throw new BusinessException("taxPercent must be between 0 and 100");
        }
        if (req.getCommissionPct() != null && (req.getCommissionPct() < 0 || req.getCommissionPct() > 100)) {
            throw new BusinessException("commissionPct must be between 0 and 100");
        }

        // Minimal currency sanity (you can enforce ISO list later)
        if (req.getDefaultCurrency() != null && req.getDefaultCurrency().length() > 10) {
            throw new BusinessException("defaultCurrency is too long");
        }

        LocalTime openTime = parseLocalTime(req.getOpenTimeLocal(), "openTimeLocal");
        LocalTime closeTime = parseLocalTime(req.getCloseTimeLocal(), "closeTimeLocal");
        if (openTime != null && closeTime != null && !openTime.isBefore(closeTime)) {
            throw new BusinessException("openTimeLocal must be before closeTimeLocal");
        }

        validateMediaMetadata(req.getImages(), "images");
        validateMediaMetadata(req.getVideos(), "videos");
    }

    // ---------- Mapping helpers ----------

    private void applyRequestToEntity(Spa spa, SpaRequestDTO req, boolean updating) {
        spa.setName(req.getName());
        spa.setDescription(req.getDescription());

        if (req.getAddress() != null) {
            spa.setAddress(toAddressEntity(req.getAddress()));
        }

        spa.setTimezone(req.getTimezone());
        spa.setPhone(req.getPhone());
        spa.setEmail(req.getEmail());
        spa.setWebsiteUrl(req.getWebsiteUrl());

        spa.setOpenTimeLocal(parseLocalTime(req.getOpenTimeLocal(), "openTimeLocal"));
        spa.setCloseTimeLocal(parseLocalTime(req.getCloseTimeLocal(), "closeTimeLocal"));
        spa.getWorkingDays().clear();
        if (req.getWorkingDays() != null) {
            spa.getWorkingDays().addAll(new HashSet<>(req.getWorkingDays()));
        }

        spa.getTherapistTypesAvailable().clear();
        if (req.getTherapistTypesAvailable() != null) {
            spa.getTherapistTypesAvailable().addAll(req.getTherapistTypesAvailable());
        }

        spa.setGstin(req.getGstin());
        spa.setBusinessRegistrationNumber(req.getBusinessRegistrationNumber());
        spa.setOwnerName(req.getOwnerName());

        if (req.getActive() != null) {
            spa.setIsActive(req.getActive());
        } else if (!updating && spa.getIsActive() == null) {
            spa.setIsActive(true);
        }

        if (req.getFeatured() != null) {
            spa.setIsFeatured(req.getFeatured());
        } else if (!updating && spa.getIsFeatured() == null) {
            spa.setIsFeatured(false);
        }

        if (req.getDefaultCurrency() != null) {
            spa.setDefaultCurrency(req.getDefaultCurrency());
        } else if (!updating && spa.getDefaultCurrency() == null) {
            spa.setDefaultCurrency("INR");
        }

        if (req.getTaxPercent() != null) {
            spa.setTaxPercent(req.getTaxPercent());
        } else if (!updating && spa.getTaxPercent() == null) {
            spa.setTaxPercent(18);
        }

        if (req.getCommissionPct() != null) {
            spa.setCommissionPct(req.getCommissionPct());
        } else if (!updating && spa.getCommissionPct() == null) {
            spa.setCommissionPct(10);
        }

        spa.setFacebookUrl(req.getFacebookUrl());
        spa.setInstagramUrl(req.getInstagramUrl());
    }

    private Address toAddressEntity(AddressDTO dto) {
        if (dto == null) return null;
        return Address.builder()
                .addressLine(dto.getAddressLine())
                .addressLine2(dto.getAddressLine2())
                .city(dto.getCity())
                .state(dto.getState())
                .postalCode(dto.getPostalCode())
                .locality(dto.getLocality())
                .landmark(dto.getLandmark())
                .country(dto.getCountry())
                .countryCode(dto.getCountryCode())
                .googlePlaceId(dto.getGooglePlaceId())
                .formattedAddress(dto.getFormattedAddress())
                .timezone(dto.getTimezone())
                .latitude((dto.getLatitude()))
                .longitude((dto.getLongitude()))
                .build();
    }

    private AddressDTO toAddressDto(Address address) {
        if (address == null) return null;
        return AddressDTO.builder()
                .addressLine(address.getAddressLine())
                .addressLine2(address.getAddressLine2())
                .city(address.getCity())
                .state(address.getState())
                .postalCode(address.getPostalCode())
                .locality(address.getLocality())
                .landmark(address.getLandmark())
                .country(address.getCountry())
                .countryCode(address.getCountryCode())
                .latitude(address.getLatitude())
                .longitude(address.getLongitude())
                .build();
    }

    private void attachTherapists(Spa spa, List<Long> therapistIds) {
        if (therapistIds == null) {
            return;
        }

        spa.getTherapists().clear();
        if (therapistIds.isEmpty()) {
            return;
        }

        List<Therapist> therapists = therapistRepository.findAllById(therapistIds);
        if (therapists.size() != therapistIds.size()) {
            throw new BusinessException("One or more therapistIds are invalid");
        }
        therapists.forEach(t -> {
            if (t.getSpa() != null && !Objects.equals(t.getSpa().getId(), spa.getId())) {
                throw new BusinessException("Therapist " + t.getId() + " already belongs to another spa");
            }
            t.setSpa(spa);
        });
        spa.getTherapists().addAll(therapists);
    }

    private void attachServices(Spa spa, List<Long> serviceIds) {
        if (serviceIds == null) {
            return;
        }
        spa.getServices().clear();
        if (serviceIds.isEmpty()) {
            return;
        }

        List<com.nirvana.application.model.Service> services = serviceRepository.findAllById(serviceIds);
        if (services.size() != serviceIds.size()) {
            throw new BusinessException("One or more serviceIds are invalid");
        }
        services.forEach(service -> {
            if (service.getSpa() != null && !Objects.equals(service.getSpa().getId(), spa.getId())) {
                throw new BusinessException("Service " + service.getId() + " belongs to another spa");
            }
            service.setSpa(spa);
        });
        spa.getServices().addAll(services);
    }

    private void attachMediaAssets(Spa spa, List<SpaMediaImageDto> images, List<SpaMediaVideoDto> videos) {
        if (images == null && videos == null) {
            return;
        }
        spa.getMediaAssets().clear();

        if (images != null) {
            images.forEach(image -> {
                validateMediaObjectKey(spa.getId(), image.getObjectKey(), MediaType.IMAGE);
                spa.getMediaAssets().add(MediaAsset.builder()
                        .spa(spa)
                        .mediaType(MediaType.IMAGE)
                        .objectKey(image.getObjectKey())
                        .title(image.getTitle())
                        .position(image.getPosition())
                        .build());
            });
        }

        if (videos != null) {
            videos.forEach(video -> {
                validateMediaObjectKey(spa.getId(), video.getObjectKey(), MediaType.VIDEO);
                spa.getMediaAssets().add(MediaAsset.builder()
                        .spa(spa)
                        .mediaType(MediaType.VIDEO)
                        .objectKey(video.getObjectKey())
                        .title(video.getTitle())
                        .position(video.getPosition())
                        .build());
            });
        }
    }

    private LocalTime parseLocalTime(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value);
        } catch (Exception e) {
            throw new BusinessException(fieldName + " must be in HH:mm:ss format");
        }
    }

    private void validateMediaObjectKey(Long spaId, String objectKey, MediaType mediaType) {
        if (spaId == null) {
            throw new BusinessException("Spa must be persisted before validating media");
        }
        String expectedFolder = mediaType == MediaType.VIDEO ? "videos" : "images";
        String pattern = String.format("^spa/%d/%s/.+", spaId, expectedFolder);
        if (objectKey == null || !objectKey.matches(pattern)) {
            throw new BusinessException("Media objectKey must be under spa/" + spaId + "/" + expectedFolder + "/");
        }
    }

    private void validateMediaMetadata(List<?> mediaList, String fieldName) {
        if (mediaList == null) {
            return;
        }
        mediaList.forEach(item -> {
            if (item instanceof SpaMediaImageDto img) {
                if (img.getObjectKey() == null || img.getObjectKey().isBlank()) {
                    throw new BusinessException("Image objectKey is required in " + fieldName);
                }
            } else if (item instanceof SpaMediaVideoDto vid) {
                if (vid.getObjectKey() == null || vid.getObjectKey().isBlank()) {
                    throw new BusinessException("Video objectKey is required in " + fieldName);
                }
            }
        });
    }

    private String formatTime(LocalTime time) {
        return time != null ? time.toString() : null;
    }

    private SpaResponseDTO toResponseDto(Spa spa) {
        List<Long> therapistIds = spa.getTherapists() != null
                ? spa.getTherapists().stream().map(Therapist::getId).toList()
                : Collections.emptyList();

        List<Long> serviceIds = spa.getServices() != null
                ? spa.getServices().stream().map(com.nirvana.application.model.Service::getId).toList()
                : Collections.emptyList();

        List<MediaAsset> sortedAssets = spa.getMediaAssets() != null
                ? spa.getMediaAssets().stream()
                .sorted((a, b) -> {
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
                })
                .toList()
                : Collections.emptyList();

        List<SpaMediaImageDto> imageDtos = sortedAssets.stream()
                .filter(asset -> asset.getMediaType() == MediaType.IMAGE)
                .map(asset -> SpaMediaImageDto.builder()
                        .objectKey(asset.getObjectKey())
                        .title(asset.getTitle())
                        .position(asset.getPosition())
                        .build())
                .toList();

        List<SpaMediaVideoDto> videoDtos = sortedAssets.stream()
                .filter(asset -> asset.getMediaType() == MediaType.VIDEO)
                .map(asset -> SpaMediaVideoDto.builder()
                        .objectKey(asset.getObjectKey())
                        .title(asset.getTitle())
                        .position(asset.getPosition())
                        .build())
                .toList();

        return SpaResponseDTO.builder()
                .id(spa.getId())
                .name(spa.getName())
                .description(spa.getDescription())
                .address(toAddressDto(spa.getAddress()))
                .timezone(spa.getTimezone())
                .phone(spa.getPhone())
                .email(spa.getEmail())
                .websiteUrl(spa.getWebsiteUrl())
                .gstin(spa.getGstin())
                .businessRegistrationNumber(spa.getBusinessRegistrationNumber())
                .ownerName(spa.getOwnerName())
                .active(spa.getIsActive())
                .verified(spa.getIsVerified())
                .featured(spa.getIsFeatured())
                .ratingAvg(spa.getRatingAvg())
                .ratingCount(spa.getRatingCount())
                .totalBookings(spa.getTotalBookings())
                .facebookUrl(spa.getFacebookUrl())
                .instagramUrl(spa.getInstagramUrl())
                .defaultCurrency(spa.getDefaultCurrency())
                .taxPercent(spa.getTaxPercent())
                .commissionPct(spa.getCommissionPct())
                .images(imageDtos)
                .videos(videoDtos)
                .therapistIds(therapistIds)
                .therapistTypesAvailable(spa.getTherapistTypesAvailable())
                .serviceIds(serviceIds)
                .openTimeLocal(formatTime(spa.getOpenTimeLocal()))
                .closeTimeLocal(formatTime(spa.getCloseTimeLocal()))
                .workingDays(spa.getWorkingDays() != null ? spa.getWorkingDays().stream().sorted().toList() : Collections.emptyList())
                .version(spa.getVersion())
                .build();
    }
}