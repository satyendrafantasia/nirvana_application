package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.MediaType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(name = "SpaMediaAttachRequest", description = "Metadata used to attach an uploaded S3 object to a spa")
public class SpaMediaAttachRequest {

    @Schema(description = "Object key already uploaded to S3", example = "spa/5001/images/uuid.jpg", required = true)
    @NotBlank
    private String objectKey;

    @Schema(description = "Type of media", example = "IMAGE", required = true)
    @NotNull
    private MediaType mediaType;

    @Schema(description = "Optional human readable title", example = "Reception")
    private String title;

    @Schema(description = "Display order for the media", example = "1")
    private Integer position;
}
