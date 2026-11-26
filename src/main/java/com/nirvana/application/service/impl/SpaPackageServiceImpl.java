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
    private final com.nirvana.application.repository.ServiceRepository services;
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

        if(request.getTitle()!=null) spaPackage.setTitle(request.getTitle().trim());
        if(request.getDescription()!=null) spaPackage.setDescription(request.getDescription().trim());
        if(request.getArchived()!=null) spaPackage.setArchived(request.getArchived());
        if(spaPackage.isArchived() && request.getStatus()==SpaPackageStatus.ACTIVE)
            throw new IllegalArgumentException("Restore an archived package before publishing");
        if(request.getServiceIds()!=null){
            var selected=services.findAllById(request.getServiceIds());
            if(selected.size()!=request.getServiceIds().size() || selected.stream().anyMatch(s->!s.getSpa().getId().equals(spaId)))
                throw new IllegalArgumentException("Choose services from this spa");
            spaPackage.getServiceIds().clear();spaPackage.getServiceIds().addAll(request.getServiceIds());
        }
        spaPackage.setValidityDays(request.getValidityDays());
        if(request.getStatus()==SpaPackageStatus.ACTIVE)spaPackage.setPublishedOnce(true);
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
        response.setTitle(spaPackage.getTitle());
        response.setDescription(spaPackage.getDescription());
        response.setValidityDays(spaPackage.getValidityDays());
        response.setArchived(spaPackage.isArchived());
        response.setServiceIds(java.util.Set.copyOf(spaPackage.getServiceIds()));
        response.setId(spaPackage.getId());
        response.setSpaId(spaPackage.getSpa().getId());
        response.setLevel(spaPackage.getLevel());
        response.setPrice(spaPackage.getPrice());
        response.setFreeSessionsCount(spaPackage.getFreeSessionsCount());
        response.setStatus(spaPackage.getStatus());
        return response;
    }
}
