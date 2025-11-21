package com.nirvana.application.model.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpaResponseDTO {

    private Long id;
    private String name;
    private String description;

    private AddressDTO address;
    private String timezone;

    private String phone;
    private String email;
    private String websiteUrl;

    private String gstin;
    private String businessRegistrationNumber;
    private String ownerName;

    private Boolean active;
    private Boolean verified;
    private Boolean featured;

    private Float ratingAvg;
    private Integer ratingCount;
    private Long totalBookings;

    private String facebookUrl;
    private String instagramUrl;

    private String defaultCurrency;
    private Integer taxPercent;
    private Integer commissionPct;

    private String imagesJson;

    private Long version;
}
