package com.nirvana.application.service.payout;

/**
 * Moves a payout's money to the spa. Implementations: {@link ManualPayoutProvider} (no transfer; ops pays by bank
 * and records the reference) and {@link RazorpayRoutePayoutProvider}.
 */
public interface PayoutProvider {
    /** Stored on the payout, e.g. MANUAL or RAZORPAY_ROUTE. */
    String name();

    /** Whether this provider moves money itself. When false, payouts wait for an admin to record a bank reference. */
    boolean automatic();

    /** Sends the money. Must not throw for a refusal by the gateway: return {@link Transfer#failed}. */
    Transfer transfer(TransferRequest request);

    /** The latest state of a transfer that was still in progress. */
    Transfer status(String transferId);

    /** Creates or updates the spa's linked account with its bank details. Only called when {@link #automatic()}. */
    Link link(LinkRequest request);

    record TransferRequest(long payoutId, String reference, String idempotencyKey, long amountCents, String currency,
                           String linkedAccountId, long spaId) {}

    enum State { COMPLETED, IN_PROGRESS, FAILED }

    record Transfer(State state, String transferId, String message) {
        public static Transfer failed(String message) { return new Transfer(State.FAILED, null, message); }
    }

    record LinkRequest(long spaId, String existingAccountId, String existingProductId, String businessName,
                       String businessType, String contactName, String email, String phone, String street1,
                       String street2, String city, String state, String postalCode, String accountHolder,
                       String ifsc, String accountNumber) {
        @Override public String toString() { return "LinkRequest[spa=" + spaId + "]"; } // never print the account number
    }

    record Link(String accountId, String productId, String error) {}
}
