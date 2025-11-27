package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.MediaType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(name = "MediaPresignRequest", description = "Request to generate a pre-signed S3 upload URL for spa media")
public class MediaPresignRequest {

    @Schema(description = "Spa identifier the media belongs to", example = "5001", required = true)
    @NotNull
    private Long spaId;

    @Schema(description = "Original file name including extension", example = "frontdesk.jpg", required = true)
    @NotBlank
    private String fileName;

    @Schema(description = "MIME type of the file", example = "image/jpeg", required = true)
    @NotBlank
    private String contentType;

    @Schema(description = "Type of media being uploaded", example = "IMAGE", required = true)
    @NotNull
    private MediaType mediaType;
}
