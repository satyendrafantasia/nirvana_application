package com.nirvana.application.service.payout;

/**
 * What Nirvana owes a spa for online money: collected less refunds, less commission on what's left.
 * Shared by the owner dashboard's "Expected payout" estimate and the real payout statement, so the two agree.
 */
public final class PayoutMath {
    private PayoutMath() {}

    public record Split(long gross, long refunds, long online, int commissionPct, long commission, long net) {}

    public static Split split(long gross, long refunds, int commissionPct) {
        long online = gross - refunds;
        long commission = Math.round(Math.max(0, online) * commissionPct / 100.0);
        return new Split(gross, refunds, online, commissionPct, commission, online - commission);
    }
}
