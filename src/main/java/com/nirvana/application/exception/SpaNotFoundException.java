package com.nirvana.application.exception;

public class SpaNotFoundException extends RuntimeException {
    public SpaNotFoundException(String message) {
        super(message);
    }
}
