package com.nirvana.application.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class LogoutRequest {

    @Schema(description = "Refresh token to revoke", example = "eyJhbGciOi...")
    private String refreshToken;

    @Schema(description = "Device fingerprint to revoke all sessions for a device")
    private String deviceFingerprint;
}
