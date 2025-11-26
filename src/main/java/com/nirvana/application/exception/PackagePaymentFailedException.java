package com.nirvana.application.exception;

public class PackagePaymentFailedException extends RuntimeException {
    public PackagePaymentFailedException(String message) {
        super(message);
    }
}
