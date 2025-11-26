package com.nirvana.application.exception;

public class CorporateDealNotFoundException extends RuntimeException {
    public CorporateDealNotFoundException(Long id) {
        super("Corporate deal not found: " + id);
    }
}
