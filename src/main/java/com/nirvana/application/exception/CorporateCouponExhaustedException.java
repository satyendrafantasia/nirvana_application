package com.nirvana.application.exception;

public class CorporateCouponExhaustedException extends RuntimeException {
    public CorporateCouponExhaustedException(Long couponId) {
        super("Corporate coupon exhausted: " + couponId);
    }
}
