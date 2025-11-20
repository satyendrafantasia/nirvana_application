package com.nirvana.application.service.impl;
import com.nirvana.application.model.dto.UploadResponse;
import com.nirvana.application.exception.StorageException;
import com.nirvana.application.service.S3Service;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3ServiceImpl implements S3Service {

    private final AmazonS3 amazonS3;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Override
    public UploadResponse uploadFile(MultipartFile file) {
        try {
            String original = StringUtils.cleanPath(file.getOriginalFilename());
            String key = UUID.randomUUID() + "_" + original;
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());
            try (InputStream is = file.getInputStream()) {
                PutObjectRequest request = new PutObjectRequest(bucketName, key, is, metadata)
                        .withCannedAcl(CannedAccessControlList.Private);
                amazonS3.putObject(request);
            }
            String url = amazonS3.getUrl(bucketName, key).toString();
            return UploadResponse.builder()
                    .key(key)
                    .fileName(original)
                    .url(url)
                    .contentType(file.getContentType())
                    .size(file.getSize())
                    .build();
        } catch (IOException e) {
            throw new StorageException("Failed to upload file", e);
        }
    }

    @Override
    public byte[] downloadFile(String key) {
        try (S3Object s3Object = amazonS3.getObject(bucketName, key);
             S3ObjectInputStream is = s3Object.getObjectContent();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            return baos.toByteArray();
        } catch (IOException | AmazonS3Exception e) {
            throw new StorageException("Failed to download file with key: " + key, e);
        }
    }

    @Override
    public void deleteFile(String key) {
        try {
            amazonS3.deleteObject(new DeleteObjectRequest(bucketName, key));
        } catch (AmazonS3Exception e) {
            throw new StorageException("Failed to delete file with key: " + key, e);
        }
    }

    @Override
    public String getFileUrl(String key) {
        return amazonS3.getUrl(bucketName, key).toString();
    }
}

