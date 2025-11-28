package com.nirvana.application.auth.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

@Getter
public class RegistrationException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;
    private final Map<String, Object> details;

    public RegistrationException(String errorCode, String message, HttpStatus status) {
        this(errorCode, message, status, null);
    }

    public RegistrationException(String errorCode, String message, HttpStatus status, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.details = details;
    }
}
