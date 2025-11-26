package com.nirvana.application.model.dto.corporate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateCorporateRequest(
        @NotBlank String name,
        @NotBlank String domain,
        @NotBlank String contactPerson,
        @Email @NotBlank String contactEmail
) {}
