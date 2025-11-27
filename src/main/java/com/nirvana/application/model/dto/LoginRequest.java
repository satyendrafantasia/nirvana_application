package com.nirvana.application.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(name = "LoginRequest", description = "Credentials used to authenticate a user.")
public class LoginRequest {

    @NotBlank
    @Schema(description = "Username or email", example = "user@example.com")
    private String username;

    @NotBlank
    @Schema(description = "User password", example = "Sup3r$ecret")
    private String password;
}
