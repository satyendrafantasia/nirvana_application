package com.nirvana.application.service.payout;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** How a Razorpay Route transfer reply becomes a payout state. */
class RazorpayRouteReplyTest {
    @Test void readsTransferStates() {
        var paid = RazorpayRoutePayoutProvider.read(new JSONObject().put("id", "trf_1").put("status", "processed"));
        assertEquals(PayoutProvider.State.COMPLETED, paid.state());
        assertEquals("trf_1", paid.transferId());
        assertEquals(PayoutProvider.State.IN_PROGRESS,
                RazorpayRoutePayoutProvider.read(new JSONObject().put("id", "trf_2").put("transfer_status", "pending")).state());
        var failed = RazorpayRoutePayoutProvider.read(new JSONObject().put("id", "trf_3").put("status", "failed")
                .put("error", new JSONObject().put("description", "Linked account is not activated")));
        assertEquals(PayoutProvider.State.FAILED, failed.state());
        assertEquals("Linked account is not activated", failed.message());
        assertEquals(PayoutProvider.State.FAILED,
                RazorpayRoutePayoutProvider.read(new JSONObject().put("id", "trf_4").put("status", "reversed")).state());
    }

    @Test void refusesWhatRouteCannotSendWithoutCallingIt() {
        var route = new RazorpayRoutePayoutProvider(null, "rzp_test_x", "secret", new PayoutProperties());
        var usd = route.transfer(new PayoutProvider.TransferRequest(1, "PO-1", "PO-1-a1", 50_000, "USD", "acc_x", 1));
        assertEquals(PayoutProvider.State.FAILED, usd.state());
        var tiny = route.transfer(new PayoutProvider.TransferRequest(1, "PO-1", "PO-1-a1", 50, "INR", "acc_x", 1));
        assertEquals(PayoutProvider.State.FAILED, tiny.state());
    }
}
