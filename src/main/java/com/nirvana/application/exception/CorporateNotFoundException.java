package com.nirvana.application.exception;

public class CorporateNotFoundException extends RuntimeException {
    public CorporateNotFoundException(Long id) {
        super("Corporate not found: " + id);
    }
}
