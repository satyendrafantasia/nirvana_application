package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.MediaType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(name = "MediaAssetResponse", description = "Metadata for a media asset associated with a spa")
public class MediaAssetResponse {
    @Schema(description = "Database identifier for the media asset", example = "9001")
    Long id;

    @Schema(description = "S3 object key", example = "spa/5001/images/uuid.jpg")
    String objectKey;

    @Schema(description = "Type of media", example = "IMAGE")
    MediaType mediaType;

    @Schema(description = "Display title", example = "Reception")
    String title;

    @Schema(description = "Display order", example = "1")
    Integer position;
}
