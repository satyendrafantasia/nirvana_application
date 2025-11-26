package com.nirvana.application.exception;

public class NoRemainingSessionsException extends RuntimeException {
    public NoRemainingSessionsException(String message) {
        super(message);
    }
}
