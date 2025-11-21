package com.nirvana.application.api;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
public class ApiErrorResponse {

    private final OffsetDateTime timestamp;
    private final int status;
    private final String error;
    private final String message;
    private final String path;
    private final List<FieldErrorDetails> fieldErrors;

    @Getter
    @Builder
    public static class FieldErrorDetails {
        private final String field;
        private final String message;
        private final Object rejectedValue;
    }
}

