package com.nirvana.application.service.impl;

import com.nirvana.application.exception.BusinessException;
import com.nirvana.application.exception.NotFoundException;
import com.nirvana.application.model.MediaAsset;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.dto.MediaAssetResponse;
import com.nirvana.application.model.dto.MediaPresignRequest;
import com.nirvana.application.model.dto.MediaPresignResponse;
import com.nirvana.application.model.dto.SpaMediaAttachRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.nirvana.application.repository.MediaAssetRepository;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.service.MediaService;
import com.nirvana.application.service.S3Service;
import com.nirvana.application.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaServiceImpl implements MediaService {

    private final SpaRepository spaRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final S3Service s3Service;

    @Override
    public MediaPresignResponse generatePresignedUpload(MediaPresignRequest request) {
        Spa spa = spaRepository.findById(request.getSpaId())
                .orElseThrow(() -> new NotFoundException("Spa not found with id: " + request.getSpaId()));

        validateOwnership(spa);
        return s3Service.generatePreSignedUploadUrl(request);
    }

    @Transactional
    @Override
    public MediaAssetResponse confirmUpload(Long spaId, SpaMediaAttachRequest request) {
        Spa spa = spaRepository.findById(spaId)
                .orElseThrow(() -> new NotFoundException("Spa not found with id: " + spaId));

        validateOwnership(spa);
        String published = s3Service.publishMedia(spaId, request.getObjectKey(), request.getMediaType());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                String staleKey = status == STATUS_COMMITTED ? request.getObjectKey() : published;
                try {
                    s3Service.deleteFile(staleKey);
                } catch (RuntimeException cleanupFailed) {
                    log.warn("Media upload cleanup pending for spa {}", spaId);
                }
            }
        });
        MediaAsset asset = MediaAsset.builder()
                .spa(spa)
                .mediaType(request.getMediaType())
                .objectKey(published)
                .title(request.getTitle())
                .position(request.getPosition())
                .build();
        MediaAsset saved = mediaAssetRepository.save(asset);

        return MediaAssetResponse.builder()
                .id(saved.getId())
                .mediaType(saved.getMediaType())
                .objectKey(saved.getObjectKey())
                .title(saved.getTitle())
                .position(saved.getPosition())
                .build();
    }

    private void validateOwnership(Spa spa) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (spa.getSpaManager() == null || spa.getSpaManager().getUser() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Spa management access required");
        }
        if (!spa.getSpaManager().getUser().getId().equals(currentUserId)) {
            throw new org.springframework.security.access.AccessDeniedException("Spa management access required");
        }
    }

}
