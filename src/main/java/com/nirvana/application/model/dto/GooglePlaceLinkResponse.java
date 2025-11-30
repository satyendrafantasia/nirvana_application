package com.nirvana.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GooglePlaceLinkResponse {
    private Long spaId;
    private String spaName;
    private String googlePlaceId;
    private String googleMapsUrl;
}
