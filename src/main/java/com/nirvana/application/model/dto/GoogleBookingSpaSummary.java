package com.nirvana.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleBookingSpaSummary {
    private Long spaId;
    private String spaName;
    private String googlePlaceId;
    private String googleMapsUrl;
    private String formattedAddress;
    private String city;
    private String state;
    private String country;
    private String timezone;
    private String defaultCurrency;
    private Boolean active;
    private Boolean verified;
    private String displayUrl;
}
