package com.nirvana.application.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(name = "SpaMediaImage", description = "Metadata for an uploaded spa image stored in S3")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpaMediaImageDto {

    @Schema(description = "S3 object key for the image", example = "spa/5001/images/uuid.jpg", required = true)
    @NotBlank
    @Size(max = 1000)
    private String objectKey;

    @Schema(description = "Human friendly title for the image", example = "Reception desk")
    private String title;

    @Schema(description = "Display order for the image", example = "1")
    private Integer position;
}
