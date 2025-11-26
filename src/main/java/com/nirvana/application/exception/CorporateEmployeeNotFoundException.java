package com.nirvana.application.exception;

public class CorporateEmployeeNotFoundException extends RuntimeException {
    public CorporateEmployeeNotFoundException(String email) {
        super("Corporate employee not found for email: " + email);
    }
}
