package com.nirvana.application.service.impl;

import com.nirvana.application.exception.SpaNotFoundException;
import com.nirvana.application.exception.SpaPackageNotFoundException;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.dto.CreateOrUpdateSpaPackageRequest;
import com.nirvana.application.model.dto.SpaPackageResponse;
import com.nirvana.application.model.enums.spa.SpaPackageStatus;
import com.nirvana.application.model.spa.SpaPackage;
import com.nirvana.application.repository.SpaPackageRepository;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.service.SpaPackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SpaPackageServiceImpl implements SpaPackageService {

    private final SpaRepository spaRepository;
    private final SpaPackageRepository spaPackageRepository;

    @Override
    @Transactional
    public SpaPackageResponse createOrUpdateSpaPackage(Long spaOwnerId, Long spaId, CreateOrUpdateSpaPackageRequest request) {
        Spa spa = spaRepository.findById(spaId).orElseThrow(() -> new SpaNotFoundException("Spa not found: " + spaId));
        validateOwnership(spaOwnerId, spa);

        SpaPackage spaPackage = spaPackageRepository.findBySpaIdAndLevel(spaId, request.getLevel())
                .orElse(SpaPackage.builder()
                        .spa(spa)
                        .level(request.getLevel())
                        .build());

        spaPackage.setPrice(request.getPrice());
        spaPackage.setFreeSessionsCount(request.getFreeSessionsCount());
        spaPackage.setStatus(request.getStatus());

        SpaPackage saved = spaPackageRepository.save(spaPackage);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpaPackageResponse> getSpaPackages(Long spaId) {
        spaRepository.findById(spaId).orElseThrow(() -> new SpaNotFoundException("Spa not found: " + spaId));
        return spaPackageRepository.findBySpaIdAndStatus(spaId, SpaPackageStatus.ACTIVE)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpaPackageResponse> getSpaPackagesForOwner(Long spaOwnerId, Long spaId) {
        Spa spa = spaRepository.findById(spaId).orElseThrow(() -> new SpaNotFoundException("Spa not found: " + spaId));
        validateOwnership(spaOwnerId, spa);
        return spaPackageRepository.findBySpaId(spaId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void validateOwnership(Long spaOwnerId, Spa spa) {
        if (spa.getSpaManager() == null || spa.getSpaManager().getUser() == null) {
            throw new SpaPackageNotFoundException("Spa manager not configured for spa: " + spa.getId());
        }
        if (!spa.getSpaManager().getUser().getId().equals(spaOwnerId)) {
            throw new IllegalStateException("User not authorized to manage packages for this spa");
        }
    }

    private SpaPackageResponse toResponse(SpaPackage spaPackage) {
        SpaPackageResponse response = new SpaPackageResponse();
        response.setId(spaPackage.getId());
        response.setSpaId(spaPackage.getSpa().getId());
        response.setLevel(spaPackage.getLevel());
        response.setPrice(spaPackage.getPrice());
        response.setFreeSessionsCount(spaPackage.getFreeSessionsCount());
        response.setStatus(spaPackage.getStatus());
        return response;
    }
}
