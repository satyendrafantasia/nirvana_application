package com.nirvana.application.controller;

import com.nirvana.application.model.dto.AdminRefundDecisionRequest;
import com.nirvana.application.model.dto.RefundResponseDto;
import com.nirvana.application.model.enums.RefundRequestStatus;
import com.nirvana.application.repository.PaymentRefundRepository;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.impl.RefundManagementServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/booking-refunds")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','PLATFORM_ADMIN')")
public class AdminBookingRefundController {
    private final PaymentRefundRepository cases;
    private final RefundManagementServiceImpl decisions;

    private final com.nirvana.application.service.impl.BookingRefundRecovery recovery;

    private final com.nirvana.application.service.AuditTrailService audit;

    public record Item(long id, long bookingId, String reference, int amountCents, String currency, String reason,
                       RefundRequestStatus status, int attempt, String gatewayRefundId, String gatewayPaymentId) {}
    public record Worklist(List<Item> items, boolean hasNext) {}

    @GetMapping
    @Transactional(readOnly = true)
    public Worklist list(@RequestParam(defaultValue="0") int page) {
        if (page < 0) throw new IllegalArgumentException("Page must be non-negative");
        var rows = cases.findByStatusInOrderByCreatedAtAscIdAsc(List.of(RefundRequestStatus.REQUESTED,
                RefundRequestStatus.FAILED, RefundRequestStatus.REVIEW_REQUIRED, RefundRequestStatus.PROCESSING), PageRequest.of(page, 25));
        return new Worklist(rows.stream().map(r -> new Item(r.getId(), r.getBooking().getId(),
                r.getBooking().getBookingReference(), r.getAmountCents(), r.getCurrency(), r.getReason(),
                r.getStatus(), r.getGatewayAttempt(), r.getGatewayRefundId(), r.getPayment().getTransactionId())).toList(), rows.hasNext());
    }

    public record HistoricalReference(String refundId) {}

    @PostMapping("/{id}/verify")
    public void verify(@PathVariable long id, @RequestBody HistoricalReference reference) {
        recovery.verifyHistorical(id, reference.refundId());
        recordReview(id, "Verified historical provider refund");
    }

    @PostMapping("/{id}/resume")
    public void resume(@PathVariable long id) {
        recovery.resume(id);
        recordReview(id, "Resumed existing Razorpay refund attempt");
    }

    @PostMapping("/{id}/recover")
    public void recover(@PathVariable long id) {
        recovery.recoverMissingHistorical(id);
        recordReview(id, "Verified missing provider refund and queued original-payment recovery");
    }

    private void recordReview(long id, String reason) {
        audit.record(com.nirvana.application.model.enums.AdminActionType.UPDATE, "REFUND", id,
                SecurityUtils.getCurrentUserId(), null, java.util.Map.of("action", reason), reason);
    }

    @PostMapping("/{id}/decision")
    public RefundResponseDto decide(@PathVariable long id, @RequestParam RefundRequestStatus status,
                                    @RequestParam int attempt, @RequestBody @Valid AdminRefundDecisionRequest request) {
        Long bookingId = cases.findBookingId(id).orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Refund not found"));
        return decisions.decide(bookingId, SecurityUtils.getCurrentUserId(), request, id, status, attempt);
    }
}
