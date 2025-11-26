package com.nirvana.application.exception;

public class NoActiveSpaPackageForUserException extends RuntimeException {
    public NoActiveSpaPackageForUserException(String message) {
        super(message);
    }
}
