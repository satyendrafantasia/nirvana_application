// src/main/java/com/nirvana/application/service/impl/SpaOnboardingServiceImpl.java
package com.nirvana.application.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.model.*;
import com.nirvana.application.model.dto.*;
import com.nirvana.application.model.enums.KycStatus;
import com.nirvana.application.repository.SpaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpaOnboardingServiceImpl implements com.nirvana.application.service.SpaOnboardingService {

    private final SpaRepository spaRepository;
    private final ObjectMapper objectMapper; // configure once as @Bean

    // TODO: inject SpaManagerService / UserService to fetch SpaManager/AppUser properly.
    // For now assume spaManager is set elsewhere or use a TODO marker.

    @Override
    @Transactional
    public SpaOnboardingSummaryResponse startOnboarding(Long ownerUserId,
                                                        SpaOnboardingStartRequest request) {

        // In prod, you’d create/find SpaManager for this ownerUserId.
        Spa spa = new Spa();
        spa.setName(request.getName());
        spa.setDescription(request.getDescription());
        spa.setPhone(request.getPhone());
        spa.setEmail(request.getEmail());
        spa.setWebsiteUrl(request.getWebsiteUrl());

        // default onboarding flags
        spa.setIsActive(false);
        spa.setIsVerified(false);
        spa.setIsFeatured(false);
        spa.setKycStatus(KycStatus.PENDING);

        if (request.getAddress() != null) {
            spa.setAddress(toAddress(request.getAddress()));
        }

        // TODO set spaManager based on ownerUserId (after you wire SpaManager/AppUser)
        // spa.setSpaManager(spaManager);

        Spa saved = spaRepository.save(spa);
        log.info("Started onboarding spa {} for ownerUserId={}", saved.getId(), ownerUserId);

        return toSummary(saved);
    }

    @Override
    @Transactional
    public SpaOnboardingSummaryResponse updateDetails(Long ownerUserId,
                                                      Long spaId,
                                                      SpaOnboardingDetailsRequest request) {
        Spa spa = findOwnedSpa(ownerUserId, spaId);

        if (request.getDescription() != null) {
            spa.setDescription(request.getDescription());
        }
        if (request.getWebsiteUrl() != null) {
            spa.setWebsiteUrl(request.getWebsiteUrl());
        }
        if (request.getFacebookUrl() != null) {
            spa.setFacebookUrl(request.getFacebookUrl());
        }
        if (request.getInstagramUrl() != null) {
            spa.setInstagramUrl(request.getInstagramUrl());
        }
        if (request.getAmenities() != null) {
            spa.setAmenities(request.getAmenities());
        }
        if (request.getTags() != null) {
            spa.setTags(request.getTags());
        }
        if (request.getMaxConcurrentServices() != null) {
            spa.setMaxConcurrentServices(request.getMaxConcurrentServices());
        }
        if (request.getMaxAdvanceBookingDays() != null) {
            spa.setMaxAdvanceBookingDays(request.getMaxAdvanceBookingDays());
        }
        if (request.getMinNoticeMinutes() != null) {
            spa.setMinNoticeMinutes(request.getMinNoticeMinutes());
        }

        Spa saved = spaRepository.save(spa);
        return toSummary(saved);
    }

    @Override
    @Transactional
    public SpaOnboardingSummaryResponse updateAddress(Long ownerUserId,
                                                      Long spaId,
                                                      AddressDTO addressDto) {
        Spa spa = findOwnedSpa(ownerUserId, spaId);

        spa.setAddress(toAddress(addressDto));

        // keep timezone column in sync if you really need the separate field
        if (spa.getAddress() != null) {
            spa.setTimezone(spa.getAddress().getTimezone());
        }

        Spa saved = spaRepository.save(spa);
        return toSummary(saved);
    }

    @Override
    @Transactional
    public SpaOnboardingSummaryResponse submitKyc(Long ownerUserId,
                                                  Long spaId,
                                                  SpaKycRequest request) {
        Spa spa = findOwnedSpa(ownerUserId, spaId);

        spa.setGstin(request.getGstin());
        spa.setBusinessRegistrationNumber(request.getBusinessRegistrationNumber());
        spa.setOwnerName(request.getOwnerName());

        // pack KYC docs into meta JSON
        Map<String, Object> meta = new HashMap<>();
        if (spa.getMetaJson() != null) {
            try {
                meta.putAll(objectMapper.readValue(spa.getMetaJson(), Map.class));
            } catch (Exception e) {
                log.warn("Failed to parse existing spa.metaJson for spaId={}", spaId, e);
            }
        }

        meta.put("kyc_pan_doc_url", request.getPanDocUrl());
        meta.put("kyc_gst_doc_url", request.getGstDocUrl());
        meta.put("kyc_address_proof_doc_url", request.getAddressProofDocUrl());
        meta.put("kyc_additional_info", request.getAdditionalInfo());

        try {
            spa.setMetaJson(objectMapper.writeValueAsString(meta));
        } catch (JsonProcessingException e) {
            log.error("Error writing metaJson for spaId={}", spaId, e);
            throw new RuntimeException("Unable to serialize KYC meta", e);
        }

        spa.setKycStatus(KycStatus.PENDING);
        spa.setKycRequestedAt(OffsetDateTime.now(ZoneOffset.UTC));

        Spa saved = spaRepository.save(spa);
        log.info("KYC submitted for spa {} by ownerUserId={}", spaId, ownerUserId);

        return toSummary(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpaOnboardingSummaryResponse> listMySpas(Long ownerUserId) {
        List<Spa> spas = spaRepository.findBySpaManager_User_Id(ownerUserId);
        return spas.stream().map(this::toSummary).collect(toList());
    }

    @Override
    @Transactional(readOnly = true)
    public SpaOnboardingSummaryResponse getMySpa(Long ownerUserId, Long spaId) {
        Spa spa = findOwnedSpa(ownerUserId, spaId);
        return toSummary(spa);
    }

    // ---------- internal helpers ----------

    private Spa findOwnedSpa(Long ownerUserId, Long spaId) {
        return spaRepository.findByIdAndSpaManager_User_Id(spaId, ownerUserId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Spa not found or not owned by user: " + spaId));
    }

    private Address toAddress(AddressDTO dto) {
        if (dto == null) {
            return null;
        }
        Address a = new Address();
        a.setAddressLine(dto.getAddressLine());
        a.setAddressLine2(dto.getAddressLine2());
        a.setCity(dto.getCity());
        a.setState(dto.getState());
        a.setPostalCode(dto.getPostalCode());
        a.setLocality(dto.getLocality());
        a.setLandmark(dto.getLandmark());
        a.setCountry(dto.getCountry());
        a.setCountryCode(dto.getCountryCode());
        a.setGooglePlaceId(dto.getGooglePlaceId());
        a.setFormattedAddress(dto.getFormattedAddress());
        a.setTimezone(dto.getTimezone());
        return a;
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
