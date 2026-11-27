package com.nirvana.application.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.nirvana.application.model.Booking;
import com.nirvana.application.model.Payment;
import com.nirvana.application.model.PaymentRefund;
import com.nirvana.application.model.enums.PaymentStatus;
import com.nirvana.application.model.enums.RefundRequestStatus;
import com.nirvana.application.model.enums.RefundStatus;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.PaymentRefundRepository;
import com.nirvana.application.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookingRefundRecovery {
    private final BookingRepository bookings;
    private final PaymentRefundRepository cases;
    private final PaymentRepository payments;
    private final RazorpayRefundGateway gateway;
    private final LoyaltyService loyalty;

    static int remaining(Payment payment) {
        return payment.getAmountCents() - Optional.ofNullable(payment.getRefundedCents()).orElse(0);
    }

    // All callers hold the booking lock. The case and booking commit before the worker calls Razorpay.
    public void lateCapture(Booking booking, Payment payment) {
        if (cases.findTopByBookingIdOrderByCreatedAtDesc(booking.getId()).isPresent() || remaining(payment) <= 0) return;
        create(booking, payment, remaining(payment), "Review online collection after the appointment closed or switched to pay at spa.",
                RefundRequestStatus.REQUESTED);
        booking.setRefundStatus(RefundStatus.REQUESTED);
    }

    public void enqueue(Booking booking, Payment payment, int amount, String reason) {
        if (!"RAZORPAY".equals(payment.getGateway()) || payment.getTransactionId() == null
                || amount <= 0 || amount > remaining(payment) - cases.voucherTotal(payment.getId())) throw new IllegalArgumentException("Invalid original-payment refund");
        var previous = cases.findTopByBookingIdOrderByCreatedAtDesc(booking.getId()).orElse(null);
        if (previous != null && previous.getStatus() == RefundRequestStatus.REVIEW_REQUIRED)
            throw new IllegalStateException("Finance must reconcile the historical refund for this payment first.");
        if (previous != null && (previous.getStatus() == RefundRequestStatus.PROCESSING || previous.getStatus() == RefundRequestStatus.FAILED)) {
            if (previous.getAmountCents() != amount) throw new IllegalStateException("Resolve the existing refund for this appointment first.");
            booking.setRefundStatus(previous.getStatus() == RefundRequestStatus.FAILED ? RefundStatus.FAILED : RefundStatus.PROCESSING);
            return;
        }
        if (previous != null && previous.getStatus() == RefundRequestStatus.REQUESTED) {
            previous.setAmountCents(amount);
            previous.setPreferVoucher(false);
            previous.setReason(reason);
            previous.setStatus(RefundRequestStatus.PROCESSING);
        } else {
            create(booking, payment, amount, reason, RefundRequestStatus.PROCESSING);
        }
        booking.setRefundStatus(RefundStatus.PROCESSING);
    }

    private void create(Booking booking, Payment payment, int amount, String reason, RefundRequestStatus status) {
        cases.save(PaymentRefund.builder().booking(booking).payment(payment).requestedBy(booking.getUser())
                .currency(payment.getCurrency()).amountCents(amount).reason(reason).status(status).preferVoucher(false).build());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void dispatch(long id) {
        Long bookingId = cases.findBookingId(id).orElseThrow();
        bookings.findByIdForUpdate(bookingId).orElseThrow();
        PaymentRefund refund = cases.findById(id).orElseThrow();
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        if (refund.getStatus() != RefundRequestStatus.PROCESSING
                || (refund.getNextCheckAt() != null && refund.getNextCheckAt().isAfter(now))) return;
        String remoteId = refund.getGatewayRefundId();
        JsonNode remote = remoteId == null ? gateway.submit(refund) : gateway.fetch(refund.getPayment().getTransactionId(), remoteId);
        apply(refund, remote);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void verifyHistorical(long id, String gatewayRefundId) {
        var booking = bookings.findByIdForUpdate(cases.findBookingId(id).orElseThrow()).orElseThrow();
        var refund = cases.findById(id).orElseThrow();
        if (refund.getStatus() != RefundRequestStatus.REVIEW_REQUIRED || refund.getGatewayAttempt() != 0)
            throw new IllegalStateException("This case no longer needs a provider reference");
        if (gatewayRefundId == null || !gatewayRefundId.matches("rfnd_[A-Za-z0-9]+"))
            throw new IllegalArgumentException("Enter the refund ID shown in Razorpay");
        if (cases.findByGatewayRefundId(gatewayRefundId).isPresent())
            throw new IllegalArgumentException("This provider refund is already attached to a case");
        JsonNode remote = gateway.fetch(refund.getPayment().getTransactionId(), gatewayRefundId);
        refund.setGatewayRefundId(gatewayRefundId);
        refund.setStatus(RefundRequestStatus.PROCESSING);
        booking.setRefundStatus(RefundStatus.PROCESSING);
        apply(refund, remote);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void recoverMissingHistorical(long id) {
        bookings.findByIdForUpdate(cases.findBookingId(id).orElseThrow()).orElseThrow();
        var refund = cases.findById(id).orElseThrow();
        if (!refund.isHistoricalPosting() || refund.getGatewayAttempt() != 0 || refund.getStatus() != RefundRequestStatus.REVIEW_REQUIRED)
            throw new IllegalStateException("Only unresolved historical refunds can be recovered");
        gateway.verifyNeverRefunded(refund.getPayment());
        refund.setGatewayAttempt(1);
        refund.setStatus(RefundRequestStatus.PROCESSING);
        refund.setNextCheckAt(null);
        refund.getBooking().setRefundStatus(RefundStatus.PROCESSING);
    }

    // Back off without committing a half-applied provider response.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void defer(long id, boolean needsReview) {
        bookings.findByIdForUpdate(cases.findBookingId(id).orElseThrow()).orElseThrow();
        var refund = cases.findById(id).orElseThrow();
        if (refund.getStatus() != RefundRequestStatus.PROCESSING) return;
        if (needsReview && refund.getGatewayAttempt() > 0) {
            refund.setStatus(RefundRequestStatus.REVIEW_REQUIRED);
            refund.setNextCheckAt(null);
        } else {
            refund.setNextCheckAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void resume(long id) {
        bookings.findByIdForUpdate(cases.findBookingId(id).orElseThrow()).orElseThrow();
        var refund = cases.findById(id).orElseThrow();
        if (refund.getStatus() != RefundRequestStatus.REVIEW_REQUIRED || refund.getGatewayAttempt() <= 0)
            throw new IllegalStateException("Only a paused gateway submission can be resumed");
        // Preserve the request body, amount and idempotency key even if the original response is unknown.
        refund.setStatus(RefundRequestStatus.PROCESSING);
        refund.setNextCheckAt(null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void webhook(String paymentId, String refundId) {
        if (paymentId == null || paymentId.isBlank() || refundId == null || refundId.isBlank())
            throw new IllegalArgumentException("Missing refund or payment ID");
        Long bookingId = payments.findBookingIdByTransactionId(paymentId).orElse(null);
        if (bookingId == null) return;
        Booking booking = bookings.findByIdForUpdate(bookingId).orElseThrow();
        var known = cases.findByGatewayRefundId(refundId).orElse(null);
        var remote = gateway.fetch(paymentId, refundId);
        if (remote == null || !paymentId.equals(remote.path("payment_id").asText()) || !refundId.equals(remote.path("id").asText()))
            throw new IllegalArgumentException("Refund identity mismatch");
        // A webhook may beat the HTTP response from the first submission.
        if (known == null) {
            known = cases.findTopByBookingIdOrderByCreatedAtDesc(bookingId)
                    .filter(r -> (r.getStatus() == RefundRequestStatus.PROCESSING || r.getStatus() == RefundRequestStatus.REVIEW_REQUIRED) && r.getGatewayRefundId() == null
                            && RazorpayRefundGateway.receipt(r).equals(remote.path("receipt").asText())).orElse(null);
        }
        if (known != null) {
            if (!bookingId.equals(known.getBooking().getId())) throw new IllegalArgumentException("Refund booking mismatch");
            if (known.getStatus() == RefundRequestStatus.PROCESSING
                    || (known.getStatus() == RefundRequestStatus.REVIEW_REQUIRED && known.getGatewayAttempt() > 0))
                apply(known, remote);
        } else if ("processed".equals(remote.path("status").asText())) {
            Payment payment = payments.findByTransactionId(paymentId).orElseThrow();
            postProcessedTotal(payment, refundId);
            if (remaining(payment) == 0 && booking.getRefundStatus() != RefundStatus.PROCESSING)
                booking.setRefundStatus(RefundStatus.COMPLETED);
        }
    }

    private void apply(PaymentRefund refund, JsonNode remote) {
        Payment payment = refund.getPayment();
        if (remote == null || remote.path("id").asText().isBlank()
                || !payment.getTransactionId().equals(remote.path("payment_id").asText())
                || !refund.getCurrency().equalsIgnoreCase(remote.path("currency").asText())
                || refund.getAmountCents() != remote.path("amount").asInt(-1)
                || (refund.getGatewayRefundId() != null && !refund.getGatewayRefundId().equals(remote.path("id").asText())))
            throw new IllegalStateException("Gateway refund does not match the local case");
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        refund.setGatewayRefundId(remote.path("id").asText());
        switch (remote.path("status").asText()) {
            case "processed" -> {
                postProcessedTotal(payment, refund.getGatewayRefundId());
                refund.setStatus(RefundRequestStatus.COMPLETED);
                refund.getBooking().setRefundStatus(RefundStatus.COMPLETED);
                refund.setProcessedAt(now);
                refund.setNextCheckAt(null);
            }
            case "failed" -> {
                refund.setStatus(RefundRequestStatus.FAILED);
                refund.getBooking().setRefundStatus(RefundStatus.FAILED);
                refund.setProcessedAt(now);
                refund.setNextCheckAt(null);
            }
            case "pending" -> {
                refund.setStatus(RefundRequestStatus.PROCESSING);
                refund.setNextCheckAt(now.plusMinutes(5));
            }
            default -> throw new IllegalStateException("Unrecognized Razorpay refund status");
        }
    }

    private void postProcessedTotal(Payment payment, String requiredRefundId) {
        int total = gateway.processedAmount(payment, requiredRefundId);
        if (total <= Optional.ofNullable(payment.getRefundedCents()).orElse(0)) return;
        payment.setRefundedCents(total);
        payment.setRefundedAt(OffsetDateTime.now(ZoneOffset.UTC));
        payment.setPaymentStatus(total == payment.getAmountCents() ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        payments.save(payment);
        loyalty.syncEarnedForBooking(payment.getBooking().getId());
    }
}
