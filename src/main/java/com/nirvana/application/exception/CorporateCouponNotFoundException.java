package com.nirvana.application.exception;

public class CorporateCouponNotFoundException extends RuntimeException {
    public CorporateCouponNotFoundException(Long id) {
        super("Corporate coupon not found: " + id);
    }
}
