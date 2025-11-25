package com.nirvana.application.model.dto;

public record MapProviderConfigResponse(
        String provider,
        String apiKey,
        String baseUrl,
        String staticStyle
) {
}
