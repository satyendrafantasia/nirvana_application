package com.nirvana.application.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompletePasswordResetRequest {
    @NotBlank
    private String passwordResetToken;

    @NotBlank
    @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
    private String newPassword;
}
