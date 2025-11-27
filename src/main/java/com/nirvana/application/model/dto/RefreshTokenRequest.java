package com.nirvana.application.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {

    @NotBlank
    @Schema(description = "Refresh token issued during login", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;

    @Schema(description = "Opaque device fingerprint to bind the refresh token to a browser/device")
    private String deviceFingerprint;
}
