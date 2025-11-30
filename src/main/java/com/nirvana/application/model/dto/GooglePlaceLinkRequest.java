package com.nirvana.application.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GooglePlaceLinkRequest {

    @NotBlank
    private String googlePlaceId;

    private String googleMapsUrl;
}
