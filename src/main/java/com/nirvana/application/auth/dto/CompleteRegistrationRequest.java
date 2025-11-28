package com.nirvana.application.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompleteRegistrationRequest {

    @NotBlank
    private String registrationToken;

    @NotBlank
    @Size(min = 2, max = 255)
    private String fullName;

    @NotBlank
    @Size(min = 8, max = 255)
    private String password;
}
