package com.nirvana.application.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@AllArgsConstructor
@Builder
@Schema(name = "MediaPresignResponse", description = "Response containing S3 pre-signed upload information")
public class MediaPresignResponse {

    @Schema(description = "Pre-signed PUT URL to upload directly to S3")
    private String uploadUrl;

    @Schema(description = "Object key that was reserved for the upload")
    private String objectKey;

    @Schema(description = "Timestamp when the signed URL expires")
    private OffsetDateTime expiresAt;
}
