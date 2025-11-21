package com.nirvana.application.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressDTO {

    private String addressLine;
    private String addressLine2;
    private String city;
    private String state;
    private String postalCode;
    private String locality;
    private String landmark;
    private String country;
    private String countryCode;
    private String googlePlaceId;
    private String formattedAddress;
    private Double latitude;
    private Double longitude;
    private String timezone;
    private String addressType;
    private String geoSource;
    private String metaJson;
}

