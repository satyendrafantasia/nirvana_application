package com.nirvana.application.service.impl;

import com.nirvana.application.exception.BusinessException;
import com.nirvana.application.exception.NotFoundException;
import com.nirvana.application.model.MediaAsset;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.dto.MediaAssetResponse;
import com.nirvana.application.model.dto.MediaPresignRequest;
import com.nirvana.application.model.dto.MediaPresignResponse;
import com.nirvana.application.model.dto.SpaMediaAttachRequest;
import com.nirvana.application.model.enums.MediaType;
import com.nirvana.application.repository.MediaAssetRepository;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.service.MediaService;
import com.nirvana.application.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {

    private final SpaRepository spaRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final S3Service s3Service;

    @Override
    public MediaPresignResponse generatePresignedUpload(MediaPresignRequest request) {
        Spa spa = spaRepository.findById(request.getSpaId())
                .orElseThrow(() -> new NotFoundException("Spa not found with id: " + request.getSpaId()));

        // TODO: add permission checks when authentication context is available
        return s3Service.generatePreSignedUploadUrl(request);
    }

    @Transactional
    @Override
    public MediaAssetResponse confirmUpload(Long spaId, SpaMediaAttachRequest request) {
        Spa spa = spaRepository.findById(spaId)
                .orElseThrow(() -> new NotFoundException("Spa not found with id: " + spaId));

        validateObjectKey(spa.getId(), request.getObjectKey(), request.getMediaType());
        if (!s3Service.objectExists(request.getObjectKey())) {
            throw new BusinessException("Uploaded object not found in S3 for key: " + request.getObjectKey());
        }

        MediaAsset asset = MediaAsset.builder()
                .spa(spa)
                .mediaType(request.getMediaType())
                .objectKey(request.getObjectKey())
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

    private void validateObjectKey(Long spaId, String objectKey, MediaType mediaType) {
        String folder = mediaType == MediaType.VIDEO ? "videos" : "images";
        String pattern = String.format("^spa/%d/%s/.+", spaId, folder);
        if (objectKey == null || !objectKey.matches(pattern)) {
            throw new BusinessException("objectKey must be within spa/" + spaId + "/" + folder + "/");
        }
    }
}
