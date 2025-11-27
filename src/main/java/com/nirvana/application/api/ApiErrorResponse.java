package com.nirvana.application.api;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
@Schema(name = "ApiError", description = "Standardized error payload returned for failed requests.")
public class ApiErrorResponse {

    @Schema(description = "Timestamp when the error was generated", example = "2024-06-01T10:15:30Z")
    private final OffsetDateTime timestamp;

    @Schema(description = "HTTP status code", example = "400")
    private final int status;

    @Schema(description = "HTTP reason phrase", example = "Bad Request")
    private final String error;

    @Schema(description = "Human readable error message", example = "Validation failed")
    private final String message;

    @Schema(description = "Request path that resulted in the error", example = "/api/bookings")
    private final String path;

    @Schema(description = "Optional field level validation errors")
    private final List<FieldErrorDetails> fieldErrors;

    @Getter
    @Builder
    @Schema(name = "FieldErrorDetails", description = "Validation error details for a specific field")
    public static class FieldErrorDetails {
        @Schema(description = "Field with invalid data", example = "email")
        private final String field;

        @Schema(description = "Validation message", example = "must be a well-formed email address")
        private final String message;

        @Schema(description = "Value that failed validation", example = "invalid-email")
        private final Object rejectedValue;
    }
}

