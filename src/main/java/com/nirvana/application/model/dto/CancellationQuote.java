package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.RefundRoute;

public record CancellationQuote(int feeCents, int refundCents, String currency, RefundRoute route, boolean packageSession) {
    public static CancellationQuote calculate(int paid, int previouslyRefunded, int percent, String currency,
                                              RefundRoute route, boolean packageSession) {
        int remaining = Math.max(0, paid - previouslyRefunded);
        int fee = Math.min(remaining, (int)Math.round(paid * (Math.min(100, Math.max(0, percent)) / 100.0)));
        return new CancellationQuote(fee, remaining - fee, currency, route, packageSession);
    }
}
