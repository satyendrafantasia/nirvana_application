package com.nirvana.application.exception;

public class NoActivePackageException extends RuntimeException {
    public NoActivePackageException(String message) {
        super(message);
    }
}
