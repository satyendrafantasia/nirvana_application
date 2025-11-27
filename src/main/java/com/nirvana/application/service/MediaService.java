package com.nirvana.application.service;

import com.nirvana.application.model.dto.MediaAssetResponse;
import com.nirvana.application.model.dto.MediaPresignRequest;
import com.nirvana.application.model.dto.MediaPresignResponse;
import com.nirvana.application.model.dto.SpaMediaAttachRequest;

public interface MediaService {
    MediaPresignResponse generatePresignedUpload(MediaPresignRequest request);

    MediaAssetResponse confirmUpload(Long spaId, SpaMediaAttachRequest request);
}
