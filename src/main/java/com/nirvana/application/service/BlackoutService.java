package com.nirvana.application.service;

import com.nirvana.application.model.BlackoutWindow;
import com.nirvana.application.model.ProviderUser;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.dto.BlackoutWindowRequest;
import com.nirvana.application.model.dto.BlackoutWindowResponse;
import com.nirvana.application.repository.BlackoutWindowRepository;
import com.nirvana.application.repository.ProviderUserRepository;
import com.nirvana.application.repository.SpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BlackoutService {

    private final BlackoutWindowRepository blackoutWindowRepository;
    private final SpaRepository spaRepository;
    private final ProviderUserRepository providerUserRepository;
    private final ProviderAuditService providerAuditService;

    @Transactional
    public BlackoutWindowResponse createBlackout(BlackoutWindowRequest request) {
        Spa spa = spaRepository.findById(request.getSpaId())
                .orElseThrow(() -> new IllegalArgumentException("Spa not found"));

        ProviderUser provider = null;
        if (request.getProviderId() != null) {
            provider = providerUserRepository.findById(request.getProviderId())
                    .orElseThrow(() -> new IllegalArgumentException("Provider not found"));
        }

        BlackoutWindow window = new BlackoutWindow();
        window.setSpa(spa);
        window.setProvider(provider);
        window.setStartTs(request.getStartTs());
        window.setEndTs(request.getEndTs());
        window.setReason(request.getReason());
        window.setCreatedBy(request.getCreatedBy());

        BlackoutWindow saved = blackoutWindowRepository.save(window);
        providerAuditService.log(spa.getId(), "SYSTEM", null, "BLACKOUT_CREATED", request.getReason());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<BlackoutWindowResponse> listForSpa(Long spaId) {
        return blackoutWindowRepository.findBySpa_Id(spaId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private BlackoutWindowResponse toResponse(BlackoutWindow entity) {
        BlackoutWindowResponse response = new BlackoutWindowResponse();
        response.setId(entity.getId());
        response.setSpaId(entity.getSpa().getId());
        response.setProviderId(entity.getProvider() != null ? entity.getProvider().getId() : null);
        response.setStartTs(entity.getStartTs());
        response.setEndTs(entity.getEndTs());
        response.setReason(entity.getReason());
        response.setCreatedBy(entity.getCreatedBy());
        return response;
    }
}
