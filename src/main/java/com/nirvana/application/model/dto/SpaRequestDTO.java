package com.nirvana.application.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpaRequestDTO {

    @NotBlank
    private String name;

    private String description;

    @NotNull
    @Valid
    private AddressDTO address;

    @NotBlank
    private String timezone;

    private String phone;
    private String email;
    private String websiteUrl;

    private String gstin;
    private String businessRegistrationNumber;
    private String ownerName;

    private Boolean active;
    private Boolean featured;

    private String facebookUrl;
    private String instagramUrl;

    private String defaultCurrency;
    private Integer taxPercent;
    private Integer commissionPct;

    // required for updates (optimistic locking)
    private Long version;
}
