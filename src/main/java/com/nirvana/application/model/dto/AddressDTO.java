package com.nirvana.application.model.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
    @Size(max = 4, message = "countryCode must be an ISO alpha-2/alpha-3 code or a short dialing code (max 4 chars)")
    private String countryCode;
    private String googlePlaceId;
    private String formattedAddress;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String timezone;
    private String addressType;
    private String geoSource;
    private String metaJson;
}

