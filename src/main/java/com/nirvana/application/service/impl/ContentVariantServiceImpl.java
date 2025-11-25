package com.nirvana.application.service.impl;

import com.nirvana.application.model.ContentVariant;
import com.nirvana.application.model.dto.ContentVariantListResponse;
import com.nirvana.application.model.dto.ContentVariantRequest;
import com.nirvana.application.model.dto.ContentVariantResponse;
import com.nirvana.application.repository.ContentVariantRepository;
import com.nirvana.application.service.ContentVariantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContentVariantServiceImpl implements ContentVariantService {

    private final ContentVariantRepository contentVariantRepository;

    @Override
    @Transactional(readOnly = true)
    public ContentVariantListResponse listActiveVariants(String experimentKey) {
        List<ContentVariant> variants = experimentKey == null
                ? contentVariantRepository.findByIsActiveTrueOrderByCreatedAtDesc()
                : contentVariantRepository.findByIsActiveTrueAndExperimentKeyOrderByCreatedAtDesc(experimentKey);

        List<ContentVariantResponse> responses = variants.stream()
                .map(this::toResponse)
                .toList();
        return new ContentVariantListResponse(responses);
    }

    @Override
    @Transactional
    public ContentVariantResponse createVariant(ContentVariantRequest request) {
        ContentVariant variant = new ContentVariant();
        variant.setExperimentKey(request.getExperimentKey());
        variant.setVariantKey(request.getVariantKey());
        variant.setContent(request.getContent());
        variant.setIsActive(request.getIsActive() == null ? Boolean.TRUE : request.getIsActive());
        ContentVariant saved = contentVariantRepository.save(variant);
        return toResponse(saved);
    }

    private ContentVariantResponse toResponse(ContentVariant variant) {
        return new ContentVariantResponse(
                variant.getId(),
                variant.getExperimentKey(),
                variant.getVariantKey(),
                variant.getContent(),
                variant.getIsActive()
        );
    }
}
