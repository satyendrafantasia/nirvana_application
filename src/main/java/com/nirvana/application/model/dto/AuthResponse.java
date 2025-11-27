package com.nirvana.application.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@Schema(name = "AuthResponse", description = "Authentication payload containing JWT token and user profile data.")
public class AuthResponse {
    @Schema(description = "JWT access token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String accessToken;

    @Schema(description = "Token type prefix", example = "Bearer")
    private String tokenType;

    @Schema(description = "Identifier of the authenticated user", example = "42")
    private Long userId;

    @Schema(description = "Username or email for the authenticated user", example = "guest@nirvana.test")
    private String username;

    @Schema(description = "Granted authorities for the user", example = "[\"ROLE_USER\"]")
    private Set<String> roles;
}
