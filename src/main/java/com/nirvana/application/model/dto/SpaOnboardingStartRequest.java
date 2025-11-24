// src/main/java/com/nirvana/application/model/dto/SpaOnboardingStartRequest.java
package com.nirvana.application.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpaOnboardingStartRequest {

    @NotBlank
    private String name;

    @Email
    private String email;

    @NotBlank
    private String phone;

    private AddressDTO address;

    // basic meta
    private String description;
    private String websiteUrl;
}
