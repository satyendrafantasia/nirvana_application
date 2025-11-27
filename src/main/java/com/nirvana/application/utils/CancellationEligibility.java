package com.nirvana.application.utils;

import com.nirvana.application.model.enums.RefundRoute;

import java.time.OffsetDateTime;

public record CancellationEligibility(
        boolean canCancel,
        String reason,
        Integer feePercent,
        RefundRoute refundRoute,
        OffsetDateTime notifyBefore
) {
    public static CancellationEligibility allowed() {
        return new CancellationEligibility(true, null, 0, RefundRoute.ORIGINAL_METHOD, null);
    }

    public static CancellationEligibility allowed(Integer feePercent, RefundRoute route, OffsetDateTime notifyBefore) {
        return new CancellationEligibility(true, null, feePercent, route, notifyBefore);
    }

    public static CancellationEligibility denied(String reason) {
        return new CancellationEligibility(false, reason, null, RefundRoute.ORIGINAL_METHOD, null);
    }
}
