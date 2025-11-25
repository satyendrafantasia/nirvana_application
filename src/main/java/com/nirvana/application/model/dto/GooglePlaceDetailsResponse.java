package com.nirvana.application.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class GooglePlaceDetailsResponse {
    private String status;
    private Result result;

    @Data
    public static class Result {
        @JsonProperty("place_id")
        private String placeId;

        private String name;

        @JsonProperty("formatted_address")
        private String formattedAddress;

        @JsonProperty("international_phone_number")
        private String internationalPhoneNumber;

        private String website;

        private Geometry geometry;

        @JsonProperty("address_components")
        private List<AddressComponent> addressComponents;
    }

    @Data
    public static class Geometry {
        private Location location;
    }

    @Data
    public static class Location {
        private BigDecimal lat;
        private BigDecimal lng;
    }

    @Data
    public static class AddressComponent {
        @JsonProperty("long_name")
        private String longName;

        @JsonProperty("short_name")
        private String shortName;

        private List<String> types;
    }
}
