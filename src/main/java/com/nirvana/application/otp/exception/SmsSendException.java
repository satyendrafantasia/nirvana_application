package com.nirvana.application.otp.exception;

public class SmsSendException extends RuntimeException {
    public SmsSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
