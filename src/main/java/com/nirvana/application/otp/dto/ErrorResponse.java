package com.nirvana.application.otp.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class ErrorResponse {
    private final String error;
    private final String message;
    private final Map<String, Object> details;
}
