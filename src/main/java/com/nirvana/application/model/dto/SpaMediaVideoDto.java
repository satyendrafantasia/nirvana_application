package com.nirvana.application.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(name = "SpaMediaVideo", description = "Metadata for an uploaded spa video stored in S3")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpaMediaVideoDto {

    @Schema(description = "S3 object key for the video", example = "spa/5001/videos/walkthrough.mp4", required = true)
    @NotBlank
    @Size(max = 1000)
    private String objectKey;

    @Schema(description = "Title for the video", example = "Full spa walkthrough")
    private String title;

    @Schema(description = "Display order for the video", example = "1")
    private Integer position;
}
