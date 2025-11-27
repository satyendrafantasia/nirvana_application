package com.nirvana.application.service;

import com.nirvana.application.model.dto.MediaPresignRequest;
import com.nirvana.application.model.dto.MediaPresignResponse;
import com.nirvana.application.model.dto.UploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface S3Service {
    UploadResponse uploadFile(MultipartFile file);

    byte[] downloadFile(String key);

    void deleteFile(String key);

    String getFileUrl(String key);

    MediaPresignResponse generatePreSignedUploadUrl(MediaPresignRequest request);

    boolean objectExists(String objectKey);
}
