package com.nirvana.application.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MessageAttachmentRequest {

    @NotBlank
    private String fileUrl;

    private String fileName;

    private String contentType;

    private Long sizeBytes;
}
