package com.nirvana.application.exception;

public class SpaAlreadyExistsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SpaAlreadyExistsException() {
        super();
    }

    public SpaAlreadyExistsException(String message) {
        super(message);
    }

    public SpaAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }

}
