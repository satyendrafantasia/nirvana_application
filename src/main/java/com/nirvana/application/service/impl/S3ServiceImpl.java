package com.nirvana.application.service.impl;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.nirvana.application.exception.BusinessException;
import com.nirvana.application.exception.StorageException;
import com.nirvana.application.model.dto.MediaPresignRequest;
import com.nirvana.application.model.dto.MediaPresignResponse;
import com.nirvana.application.model.dto.UploadResponse;
import com.nirvana.application.model.enums.MediaType;
import com.nirvana.application.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3ServiceImpl implements S3Service {
    private final AmazonS3 amazonS3;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Override
    public UploadResponse uploadFile(MultipartFile file) {
        UploadFormat format = UploadFormat.forMime(file.getContentType());
        try (InputStream input = file.getInputStream()) {
            byte[] bytes = UploadFormat.read(input, file.getSize());
            String key = "private/files/" + UUID.randomUUID() + "." + format.extension;
            writeVerified(key, format, bytes);
            return UploadResponse.builder().key(key).fileName(file.getOriginalFilename()).url(getFileUrl(key))
                    .contentType(format.mime).size((long) bytes.length).build();
        } catch (IOException e) {
            throw new StorageException("Could not read the upload", e);
        }
    }

    private void writeVerified(String key, UploadFormat format, byte[] bytes) {
        format.verify(bytes);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(format.mime);
        metadata.setContentLength(bytes.length);
        metadata.setContentDisposition(format.kind == null ? "attachment" : "inline");
        // Objects inherit the bucket's private default. Do not send ACLs to ownership-enforced buckets.
        amazonS3.putObject(new PutObjectRequest(bucketName, key, new ByteArrayInputStream(bytes), metadata));
    }

    @Override
    public byte[] downloadFile(String key) {
        try (S3Object object = amazonS3.getObject(bucketName, key)) {
            return UploadFormat.read(object.getObjectContent(), object.getObjectMetadata().getContentLength());
        } catch (IOException | AmazonS3Exception e) {
            throw new StorageException("Could not download the file", e);
        }
    }

    @Override
    public void deleteFile(String key) {
        amazonS3.deleteObject(bucketName, key);
    }

    @Override
    public String getFileUrl(String key) {
        if (key != null && key.startsWith("https://")) {
            URI uri = URI.create(key);
            if (uri.getHost() != null && uri.getUserInfo() == null) return uri.toASCIIString();
        }
        return signedRead(key, new ResponseHeaderOverrides().withContentType("application/octet-stream")
                .withContentDisposition("attachment"));
    }

    private String signedRead(String key, ResponseHeaderOverrides headers) {
        return amazonS3.generatePresignedUrl(new GeneratePresignedUrlRequest(bucketName, key)
                .withMethod(HttpMethod.GET).withResponseHeaders(headers)
                .withExpiration(Date.from(Instant.now().plusSeconds(600)))).toString();
    }

    @Override
    public String getSpaMediaUrl(Long spaId, MediaType kind, String key) {
        if (spaId == null || kind == null || key == null) return null;
        try {
            if (key.startsWith("https://")) {
                URI uri = URI.create(key);
                return uri.getHost() != null && uri.getUserInfo() == null ? uri.toASCIIString() : null;
            }
            String prefix = "spa/" + spaId + (kind == MediaType.VIDEO ? "/videos/" : "/images/");
            if (!key.startsWith(prefix)) return null;
            String filename = key.substring(prefix.length());
            if (filename.isBlank() || filename.equals(".") || filename.equals("..") || filename.contains("/")
                    || filename.contains("\\") || filename.chars().anyMatch(Character::isISOControl)) return null;
            return signedRead(key, new ResponseHeaderOverrides().withContentDisposition("inline"));
        } catch (IllegalArgumentException | com.amazonaws.SdkClientException unavailable) {
            // A broken legacy media entry or missing storage credentials must not hide the service menu.
            return null;
        }
    }

    @Override
    public MediaPresignResponse generatePreSignedUploadUrl(MediaPresignRequest request) {
        UploadFormat format = UploadFormat.forMime(request.getContentType());
        if (format.kind == null || format.kind != request.getMediaType()) throw new BusinessException("Choose the matching media type");
        if (request.getSizeBytes() == null) throw new BusinessException("Include the file size when requesting an upload. Refresh the uploader and try again.");
        UploadFormat.checkSize(request.getSizeBytes());
        String key = "incoming/spa/" + request.getSpaId() + "/" + UUID.randomUUID() + "." + format.extension;
        Instant expiry = Instant.now().plusSeconds(600);
        var signed = new GeneratePresignedUrlRequest(bucketName, key).withMethod(HttpMethod.PUT)
                .withContentType(format.mime).withExpiration(Date.from(expiry));
        // Browsers automatically supply the exact Content-Length for a File body.
        signed.putCustomRequestHeader("Content-Length", request.getSizeBytes().toString());
        String url = amazonS3.generatePresignedUrl(signed).toString();
        String signedHeaders = java.util.Arrays.stream(URI.create(url).getQuery().split("&"))
                .filter(param -> param.startsWith("X-Amz-SignedHeaders="))
                .map(param -> param.substring("X-Amz-SignedHeaders=".length())).findFirst().orElse("");
        if (!java.util.Arrays.asList(signedHeaders.split(";")).contains("content-length")) {
            throw new StorageException("Storage signing must enforce the upload size");
        }
        return MediaPresignResponse.builder().uploadUrl(url).objectKey(key)
                .expiresAt(expiry.atOffset(ZoneOffset.UTC)).build();
    }

    @Override
    public String publishMedia(long spaId, String key, MediaType kind) {
        String prefix = "incoming/spa/" + spaId + "/";
        if (key == null || !key.startsWith(prefix) ||
                !key.substring(prefix.length()).matches("[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}\\.[a-z0-9]+")) {
            throw new BusinessException("Choose a pending upload for this spa");
        }
        // Inspect and publish the same bytes under a fresh key. Reusing a pending PUT cannot replace published content.
        try (S3Object object = amazonS3.getObject(bucketName, key)) {
            var metadata = object.getObjectMetadata();
            var format = UploadFormat.forMime(metadata.getContentType());
            if (format.kind == null || format.kind != kind || !key.endsWith("." + format.extension)) {
                throw new BusinessException("Uploaded media type does not match");
            }
            byte[] bytes = UploadFormat.read(object.getObjectContent(), metadata.getContentLength());
            String folder = kind == MediaType.VIDEO ? "videos" : "images";
            String published = "spa/" + spaId + "/" + folder + "/" + UUID.randomUUID() + "." + format.extension;
            writeVerified(published, format, bytes);
            return published;
        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() == 404) throw new BusinessException("Pending upload is missing or expired");
            throw new StorageException("Could not finalize the upload", e);
        } catch (IOException e) {
            throw new StorageException("Could not read the pending upload", e);
        }
    }
}
