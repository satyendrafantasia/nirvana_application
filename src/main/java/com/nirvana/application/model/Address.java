package com.nirvana.application.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "metaJson")
@EqualsAndHashCode
public class Address {


    @Column(name = "address_line1", nullable = false, length = 255)
    private String addressLine;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(length = 100, nullable = false)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(length = 255)
    private String locality;

    @Column(length = 255)
    private String landmark;

    @Column(length = 100)
    private String country;

    @Column(length = 4)
    private String countryCode; // ISO code

    // Google map integration
    @Column(name = "google_place_id", length = 255, insertable = false, updatable = false)
    private String googlePlaceId;

    @Column(name = "formatted_address", length = 500)
    private String formattedAddress;

    // Latitude/Longitude (use BigDecimal so precision/scale are meaningful)
    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(length = 50)
    private String timezone;

    @Column(length = 50)
    private String addressType; // HOME / OFFICE / SPA_BRANCH etc.

    @Column(length = 50)
    private String geoSource; // MANUAL, GPS, GOOGLE_AUTOCOMPLETE

    // Flexible extension
    @Column(name = "meta", columnDefinition = "json")
    private String metaJson;

    // equals & hashcode remain same as yours
}
