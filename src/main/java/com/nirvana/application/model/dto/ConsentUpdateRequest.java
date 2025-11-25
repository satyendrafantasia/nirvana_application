package com.nirvana.application.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConsentUpdateRequest {

    private Boolean marketingOptIn;

    private Boolean acceptPrivacyPolicy;

    @NotBlank
    @Size(max = 64)
    private String consentVersion;

    @Size(max = 128)
    private String consentSource;

    @Size(max = 64)
    private String timezone;

    @Size(max = 16)
    private String locale;
}
