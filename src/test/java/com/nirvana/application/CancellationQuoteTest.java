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
    @Test void splitPaymentKeepsEachTenderAndItsRounding() {
        var quote = CancellationQuote.calculate(501,0,25,"INR",RefundRoute.ORIGINAL_METHOD,false)
                .withTenders(CancellationQuote.Tender.from(501,75), CancellationQuote.Tender.from(0,75));
        assertEquals(125,quote.feeCents());
        assertEquals(376,quote.refundCents());
        assertEquals(126,quote.walletFeeCents());
        assertEquals(375,quote.walletRefundCents());
        assertTrue(quote.hasStoredTender());
    }
    @Test void fullyWithheldPaymentNeedsNoGatewayRefund() {
        assertEquals(0, CancellationQuote.calculate(100, 0, 100, "INR", RefundRoute.ORIGINAL_METHOD, false).refundCents());
    }
}
