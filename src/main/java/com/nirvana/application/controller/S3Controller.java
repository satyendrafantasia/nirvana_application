package com.nirvana.application.controller;

import com.nirvana.application.model.dto.UploadResponse;
import com.nirvana.application.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/s3")
@RequiredArgsConstructor
public class S3Controller {

    private final S3Service s3Service;

    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> upload(@RequestParam("file") MultipartFile file) {
        UploadResponse resp = s3Service.uploadFile(file);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/download/{key}")
    public ResponseEntity<byte[]> download(@PathVariable String key) {
        byte[] data = s3Service.downloadFile(key);
        String url = s3Service.getFileUrl(key);
        // Content type not always known; letting client handle if absent
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + key + "\"");
        headers.add("X-File-Url", url);
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> delete(@PathVariable String key) {
        s3Service.deleteFile(key);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/url/{key}")
    public ResponseEntity<String> getUrl(@PathVariable String key) {
        String url = s3Service.getFileUrl(key);
        return ResponseEntity.ok(url);
    }
}
