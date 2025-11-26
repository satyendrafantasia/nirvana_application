package com.nirvana.application.exception;

public class NoActiveCorporateCouponException extends RuntimeException {
    public NoActiveCorporateCouponException(Long userId) {
        super("No active corporate coupon available for user " + userId);
    }
}
