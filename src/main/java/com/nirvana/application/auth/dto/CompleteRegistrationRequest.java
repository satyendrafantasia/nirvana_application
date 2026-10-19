package com.nirvana.application.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompleteRegistrationRequest {

    public enum AccountType { CUSTOMER, SPA_OWNER }
    @jakarta.validation.constraints.NotNull
    private AccountType accountType = AccountType.CUSTOMER;

    @NotBlank
    private String registrationToken;

    @jakarta.validation.constraints.AssertTrue(message="Privacy consent is required")
    private boolean acceptPrivacyPolicy;

    @NotBlank
    @Size(min = 2, max = 255)
    private String fullName;

    @NotBlank
    @Size(min = 8, max = 255)
    private String password;
}
