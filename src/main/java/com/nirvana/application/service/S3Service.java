package com.nirvana.application.service;

import org.springframework.web.multipart.MultipartFile;
import com.nirvana.application.model.dto.UploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface S3Service {
    UploadResponse uploadFile(MultipartFile file);
    byte[] downloadFile(String key);
    void deleteFile(String key);
    String getFileUrl(String key);
}
