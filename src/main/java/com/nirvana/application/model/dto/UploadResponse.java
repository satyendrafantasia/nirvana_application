package com.nirvana.application.model.dto;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UploadResponse {
    private String key;
    private String fileName;
    private String url;
    private String contentType;
    private long size;
}

