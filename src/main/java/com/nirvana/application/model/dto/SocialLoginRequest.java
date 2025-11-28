package com.nirvana.application.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SocialLoginRequest", description = "Request to initiate social OAuth2 login")
public class SocialLoginRequest {

    @NotBlank
    @Schema(description = "OAuth2 provider identifier", example = "google")
    private String provider;

    @Schema(description = "Optional redirect URI to use after provider authentication", example = "https://app.nirvana.test/oauth2/callback")
    private String redirectUri;
}
