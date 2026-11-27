package com.nirvana.application.rewards;

import com.nirvana.application.model.Booking;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Cancellation credits stay in the customer's account and can be spent in their original currency. */
@Service
@RequiredArgsConstructor
public class BookingWallet {
    private final JdbcTemplate db;

    public int quote(long user, String currency, int total) {
        long balance = db.queryForObject("SELECT COALESCE(MAX(balance_cents),0) FROM booking_wallet_balance WHERE user_id=? AND currency=?", Long.class, user, currency);
        if (balance == 0) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "You have no wallet credit in this currency. Review your payment method.");
        return (int) Math.min(balance, total);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void credit(Booking booking, String currency, int amount) {
        if (amount <= 0) throw new IllegalArgumentException("A wallet refund must be positive");
        long user = booking.getUser().getId();
        db.update("INSERT INTO booking_wallet_balance(user_id,currency,balance_cents) VALUES(?,?,0) ON DUPLICATE KEY UPDATE balance_cents=balance_cents",user,currency);
        var existing = db.queryForList("SELECT amount_cents FROM booking_wallet_credit WHERE booking_id=? FOR UPDATE",Integer.class,booking.getId());
        if (!existing.isEmpty()) {
            if (existing.get(0) != amount) throw new IllegalStateException("A different wallet refund was already issued");
            return;
        }
        db.update("INSERT INTO booking_wallet_credit(booking_id,user_id,currency,amount_cents) VALUES(?,?,?,?)",booking.getId(),user,currency,amount);
        db.update("UPDATE booking_wallet_balance SET balance_cents=balance_cents+? WHERE user_id=? AND currency=?",amount,user,currency);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int spend(Booking booking, int amount) {
        long user = booking.getUser().getId();
        if (amount <= 0) throw new IllegalArgumentException("A wallet payment must be positive");
        int changed = db.update("UPDATE booking_wallet_balance SET balance_cents=balance_cents-? WHERE user_id=? AND currency=? AND balance_cents>=?",
            amount,user,booking.getCurrency(),amount);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Your wallet balance changed. Review your booking total and try again.");
        db.update("INSERT INTO booking_wallet_spend(booking_id,user_id,currency,amount_cents,spent_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6))",booking.getId(),user,booking.getCurrency(),amount);
        return amount;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int returnForBooking(Booking booking, int percent) {
        var pending = db.queryForList("SELECT amount_cents FROM booking_wallet_spend WHERE booking_id=? AND returned_cents IS NULL FOR UPDATE",Integer.class,booking.getId());
        if (pending.isEmpty()) return 0;
        int amount = com.nirvana.application.model.dto.CancellationQuote.Tender.from(pending.get(0), percent).refund();
        db.update("UPDATE booking_wallet_spend SET returned_cents=?,returned_at=UTC_TIMESTAMP(6) WHERE booking_id=?",amount,booking.getId());
        db.update("UPDATE booking_wallet_balance SET balance_cents=balance_cents+? WHERE user_id=? AND currency=?",amount,booking.getUser().getId(),booking.getCurrency());
        return amount;
    }

    public com.nirvana.application.model.dto.CancellationQuote.Tender quoteReturn(long bookingId, int returnPercent) {
        int paid = db.queryForObject("SELECT COALESCE(SUM(amount_cents),0) FROM booking_wallet_spend WHERE booking_id=? AND returned_cents IS NULL",
                Integer.class, bookingId);
        return com.nirvana.application.model.dto.CancellationQuote.Tender.from(paid, returnPercent);
    }

    public int netSpent(long bookingId) {
        return db.queryForObject("SELECT COALESCE(SUM(amount_cents-COALESCE(returned_cents,0)),0) FROM booking_wallet_spend WHERE booking_id=?",Integer.class,bookingId);
    }
}
