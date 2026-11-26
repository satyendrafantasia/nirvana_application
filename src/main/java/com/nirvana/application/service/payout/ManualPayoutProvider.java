package com.nirvana.application.service.payout;

/** No gateway: statements are produced and the spa is paid by bank transfer outside Nirvana. */
public class ManualPayoutProvider implements PayoutProvider {
    public static final String NAME = "MANUAL";
    @Override public String name() { return NAME; }
    @Override public boolean automatic() { return false; }
    @Override public Transfer transfer(TransferRequest request) {
        return Transfer.failed("Automatic transfers are off; pay by bank transfer and record the reference");
    }
    @Override public Transfer status(String transferId) { return new Transfer(State.COMPLETED, transferId, null); }
    @Override public Link link(LinkRequest request) { return new Link(null, null, null); }
}
