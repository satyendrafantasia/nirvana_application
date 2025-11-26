package com.nirvana.application.exception;

public class CorporateCouponExpiredException extends RuntimeException {
    public CorporateCouponExpiredException(Long couponId) {
        super("Corporate coupon expired: " + couponId);
    }
}
