package com.nirvana.application.service.impl;

import com.nirvana.application.exception.BusinessException;
import com.nirvana.application.exception.NotFoundException;
import com.nirvana.application.model.dto.AddressDTO;
import com.nirvana.application.model.dto.SpaRequestDTO;
import com.nirvana.application.model.dto.SpaResponseDTO;
import com.nirvana.application.model.Address;
import com.nirvana.application.model.Spa;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.service.SpaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class SpaServiceImpl implements SpaService {

    private final SpaRepository spaRepository;

    @Transactional
    @Override
    public SpaResponseDTO createSpa(SpaRequestDTO request) {
        validateBusiness(request);

        Spa spa = new Spa();
        applyRequestToEntity(spa, request, false);
        Spa saved = spaRepository.save(spa);
        return toResponseDto(saved);
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
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
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

    private SpaResponseDTO toResponseDto(Spa spa) {
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
                .imagesJson(spa.getImagesJson())
                .version(spa.getVersion())
                .build();
    }
}