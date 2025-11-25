package com.nirvana.application.utils;

public record CancellationEligibility(boolean canCancel, String reason) {
    public static CancellationEligibility allowed() {
        return new CancellationEligibility(true, null);
    }

    public static CancellationEligibility denied(String reason) {
        return new CancellationEligibility(false, reason);
    }
}
