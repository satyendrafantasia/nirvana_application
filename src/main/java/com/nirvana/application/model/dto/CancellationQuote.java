package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.RefundRoute;

public record CancellationQuote(int feeCents, int refundCents, String currency, RefundRoute route, boolean packageSession,
                                int walletFeeCents, int walletRefundCents, int giftCardFeeCents, int giftCardRefundCents) {
    /** The original payment uses the existing rounding rule. Wallet and gift-card balances return whole paise. */
    public static CancellationQuote calculate(int paid, int previouslyRefunded, int percent, String currency,
                                              RefundRoute route, boolean packageSession) {
        int remaining = Math.max(0, paid - previouslyRefunded);
        int fee = Math.min(remaining, (int)Math.round(paid * (Math.min(100, Math.max(0, percent)) / 100.0)));
        return new CancellationQuote(fee, remaining - fee, currency, route, packageSession, 0, 0, 0, 0);
    }

    public record Tender(int fee, int refund) {
        public static Tender from(int paid, int returnPercent) {
            int back = (int)((long)paid * Math.max(0, Math.min(100, returnPercent)) / 100);
            return new Tender(paid - back, back);
        }
    }

    public CancellationQuote withTenders(Tender wallet, Tender gift) {
        return new CancellationQuote(feeCents, refundCents, currency, route, packageSession,
                wallet.fee(), wallet.refund(), gift.fee(), gift.refund());
    }

    public boolean hasStoredTender() {
        return walletFeeCents > 0 || walletRefundCents > 0 || giftCardFeeCents > 0 || giftCardRefundCents > 0;
    }
}
