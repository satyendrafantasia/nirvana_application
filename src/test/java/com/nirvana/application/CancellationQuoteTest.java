package com.nirvana.application;

import com.nirvana.application.model.dto.CancellationQuote;
import com.nirvana.application.model.enums.RefundRoute;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CancellationQuoteTest {
    @Test void feeIsAppliedOnceAndPriorRefundsReduceTheRemainingRefund() {
        var quote = CancellationQuote.calculate(200000, 10000, 25, "INR", RefundRoute.WALLET_CREDIT, false);
        assertEquals(50000, quote.feeCents());
        assertEquals(140000, quote.refundCents());
    }
    @Test void fullyWithheldPaymentNeedsNoGatewayRefund() {
        assertEquals(0, CancellationQuote.calculate(100, 0, 100, "INR", RefundRoute.ORIGINAL_METHOD, false).refundCents());
    }
}
