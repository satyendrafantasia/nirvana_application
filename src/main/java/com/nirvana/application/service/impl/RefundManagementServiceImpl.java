package com.nirvana.application.service.impl;

import com.nirvana.application.model.*;
import com.nirvana.application.model.dto.*;
import com.nirvana.application.model.enums.AdminActionType;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.PaymentStatus;
import com.nirvana.application.model.enums.RefundRequestStatus;
import com.nirvana.application.model.enums.RefundStatus;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.PaymentRefundRepository;
import com.nirvana.application.repository.PaymentRepository;
import com.nirvana.application.repository.UserRepository;
import com.nirvana.application.repository.VoucherRepository;
import com.nirvana.application.service.AuditTrailService;
import com.nirvana.application.service.impl.PaymentService;
import com.nirvana.application.service.RefundManagementService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundManagementServiceImpl implements RefundManagementService {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository paymentRefundRepository;

    private final VoucherRepository voucherRepository;
    private final UserRepository userRepository;
    private final AuditTrailService auditTrailService;
    private final LoyaltyService loyalty;

    @Override
    @Transactional
    public RefundResponseDto requestRefund(Long bookingId, Long userId, RefundRequestDto request) {
        Booking booking = bookingRepository.findOwnedForUpdate(bookingId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found for user"));

        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.NO_SHOW) {
            throw new IllegalStateException("Booking already cancelled or closed");
        }

        if (paymentRefundRepository.existsByBookingIdAndStatusIn(bookingId, List.of(RefundRequestStatus.REQUESTED,
                RefundRequestStatus.PROCESSING, RefundRequestStatus.FAILED, RefundRequestStatus.REVIEW_REQUIRED))) {
            throw new IllegalStateException("Refund already requested for this booking");
        }

        Payment payment = paymentRepository
                .findFirstByBookingIdAndPaymentStatusIn(
                        bookingId,
                        List.of(PaymentStatus.CAPTURED, PaymentStatus.COMPLETED, PaymentStatus.PARTIALLY_REFUNDED))
                .orElseThrow(() -> new IllegalStateException("No successful payment found for booking"));

        int available = BookingRefundRecovery.remaining(payment) - paymentRefundRepository.voucherTotal(payment.getId());
        int cappedAmount = request.amountCents() == null ? available : request.amountCents();
        if (cappedAmount <= 0 || cappedAmount > available) throw new IllegalArgumentException("Amount exceeds the remaining refundable balance");

        PaymentRefund refund = PaymentRefund.builder()
                .booking(booking)
                .payment(payment)
                .requestedBy(resolveUser(userId))
                .amountCents(cappedAmount)
                .currency(payment.getCurrency())
                .reason(request.reason())
                .status(RefundRequestStatus.REQUESTED)
                .preferVoucher(Boolean.TRUE.equals(request.preferVoucher()))
                .build();

        PaymentRefund saved = paymentRefundRepository.save(refund);
        booking.setRefundStatus(RefundStatus.REQUESTED);
        bookingRepository.save(booking);

        log.info("Refund requested for booking {} by user {} (preferVoucher={})",
                bookingId, userId, request.preferVoucher());

        return toDto(saved);
    }

    @Override
    @Transactional
    public RefundResponseDto approveOrProcessRefund(Long bookingId, Long adminUserId, AdminRefundDecisionRequest request) {
        return decide(bookingId, adminUserId, request, null, null, null);
    }

    @Transactional
    public RefundResponseDto decide(long bookingId, long adminUserId, AdminRefundDecisionRequest request,
                                    Long expectedCase, RefundRequestStatus expectedStatus, Integer expectedAttempt) {
        bookingRepository.findByIdForUpdate(bookingId).orElseThrow(() -> new EntityNotFoundException("Booking not found"));
        PaymentRefund refund = (expectedCase == null ? paymentRefundRepository.findTopByBookingIdOrderByCreatedAtDesc(bookingId)
                : paymentRefundRepository.findById(expectedCase).filter(r -> r.getBooking().getId().equals(bookingId)))
                .orElseThrow(() -> new EntityNotFoundException("No refund request found for booking"));

        if (expectedCase != null && (!expectedCase.equals(refund.getId()) || expectedStatus != refund.getStatus()
                || !java.util.Objects.equals(expectedAttempt, refund.getGatewayAttempt())))
            throw new IllegalStateException("This refund has changed. Refresh its status before making a decision.");
        if (refund.getStatus() == RefundRequestStatus.REVIEW_REQUIRED)
            throw new IllegalStateException("Verify and attach the original Razorpay refund before deciding this case.");
        if (refund.getStatus() == RefundRequestStatus.FAILED && expectedStatus != RefundRequestStatus.FAILED)
            throw new IllegalStateException("Review the failed attempt in the booking refund worklist before retrying.");
        if (List.of(RefundRequestStatus.COMPLETED, RefundRequestStatus.REJECTED, RefundRequestStatus.PROCESSING).contains(refund.getStatus())) {
            return toDto(refund);
        }

        if (!request.approve()) {
            refund.setStatus(RefundRequestStatus.REJECTED);
            refund.setReason(request.reason());
            refund.setProcessedBy(resolveUser(adminUserId));
            refund.setProcessedAt(OffsetDateTime.now(ZoneOffset.UTC));
            PaymentRefund saved = paymentRefundRepository.save(refund);
            updateBookingRefundStatus(refund.getBooking(), RefundStatus.REJECTED);
            auditTrailService.record(
                    AdminActionType.STATUS_CHANGE,
                    "REFUND",
                    refund.getId(),
                    adminUserId,
                    Map.of("previousStatus", RefundRequestStatus.REQUESTED.name()),
                    Map.of("status", RefundRequestStatus.REJECTED.name(), "reason", request.reason()),
                    request.reason());
            return toDto(saved);
        }

        int amount = request.amountCents() == null ? refund.getAmountCents() : request.amountCents();
        int available = BookingRefundRecovery.remaining(refund.getPayment()) - paymentRefundRepository.voucherTotal(refund.getPayment().getId());
        // Historical cases already posted to the customer ledger. Retry only the positively identified failed amount.
        boolean historicalRetry = refund.getStatus() == RefundRequestStatus.FAILED && refund.isHistoricalPosting()
                && refund.getGatewayRefundId() != null && amount == refund.getAmountCents() && !request.issueVoucher();
        if (amount <= 0 || amount > refund.getAmountCents() || (!historicalRetry && amount > available))
            throw new IllegalArgumentException("Approval exceeds the remaining refundable balance");
        if (refund.getStatus() == RefundRequestStatus.FAILED) {
            refund.setGatewayAttempt(refund.getGatewayAttempt() + 1);
            refund.setGatewayRefundId(null);
        }
        refund.setStatus(RefundRequestStatus.PROCESSING);
        refund.setAmountCents(amount);
        refund.setReason(request.reason());
        refund.setProcessedBy(resolveUser(adminUserId));
        refund.setProcessedAt(null);
        refund.setNextCheckAt(null);

        if (Boolean.TRUE.equals(refund.getPreferVoucher()) || request.issueVoucher()) {
            Voucher voucher = issueVoucherInternal(refund.getBooking(), request.reason(), amount, adminUserId);
            refund.setVoucherCode(voucher.getCode());
            refund.setStatus(RefundRequestStatus.COMPLETED);
            refund.setReason(request.reason());
            refund.setProcessedAt(OffsetDateTime.now(ZoneOffset.UTC));
            PaymentRefund saved = paymentRefundRepository.save(refund);
            updateBookingRefundStatus(refund.getBooking(), RefundStatus.COMPLETED);
            auditTrailService.record(
                    AdminActionType.UPDATE,
                    "REFUND",
                    refund.getId(),
                    adminUserId,
                    Map.of("previousStatus", RefundRequestStatus.PROCESSING.name()),
                    Map.of("status", RefundRequestStatus.COMPLETED.name(), "voucherCode", voucher.getCode()),
                    request.reason());
            return toDto(saved);
        }

        if (!"RAZORPAY".equals(refund.getPayment().getGateway()) || refund.getPayment().getTransactionId() == null)
            throw new IllegalStateException("Cannot submit a refund without a captured Razorpay payment");
        updateBookingRefundStatus(refund.getBooking(), RefundStatus.PROCESSING);
        auditTrailService.record(AdminActionType.UPDATE, "REFUND", refund.getId(), adminUserId, null,
                Map.of("status", RefundRequestStatus.PROCESSING.name(), "amountCents", amount), request.reason());
        return toDto(paymentRefundRepository.save(refund));
    }

    @Override
    @Transactional(readOnly = true)
    public RefundResponseDto latestRefundForBooking(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found for user"));
        return paymentRefundRepository.findTopByBookingIdOrderByCreatedAtDesc(booking.getId())
                .map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException("No refund found for booking"));
    }

    @Override
    @Transactional
    public VoucherResponse issueVoucher(Long bookingId, Long adminUserId, VoucherIssueRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        Voucher voucher = issueVoucherInternal(booking, request.note(), request.amountCents(), adminUserId, request.currency(), request.expiresAt());
        return VoucherResponse.builder()
                .code(voucher.getCode())
                .amountCents(voucher.getAmountCents())
                .currency(voucher.getCurrency())
                .note(voucher.getNote())
                .expiresAt(voucher.getExpiresAt())
                .issuedAt(voucher.getCreatedAt())
                .build();
    }

    private Voucher issueVoucherInternal(Booking booking, String note, Integer amountCents, Long adminUserId) {
        return issueVoucherInternal(booking, note, amountCents, adminUserId, booking.getCurrency(), null);
    }

    private Voucher issueVoucherInternal(Booking booking, String note, Integer amountCents, Long adminUserId, String currency, OffsetDateTime expiresAt) {
        String voucherCode = generateVoucherCode();
        Voucher voucher = Voucher.builder()
                .booking(booking)
                .user(booking.getUser())
                .amountCents(amountCents != null ? amountCents : booking.getPriceCents())
                .currency(currency != null ? currency : booking.getCurrency())
                .note(note)
                .code(voucherCode)
                .issuedBy(String.valueOf(adminUserId))
                .expiresAt(expiresAt)
                .build();

        return voucherRepository.save(voucher);
    }

    private String generateVoucherCode() {
        while (true) {
            String code = "VCH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
            if (!voucherRepository.existsByCode(code)) {
                return code;
            }
        }
    }

    private void updateBookingRefundStatus(Booking booking, RefundStatus status) {
        booking.setRefundStatus(status);
        bookingRepository.save(booking);
        // Points earned on a completed booking follow the money: a refund takes its share back.
        if (status == RefundStatus.COMPLETED) loyalty.syncEarnedForBooking(booking.getId());
    }

    private RefundResponseDto toDto(PaymentRefund refund) {
        return RefundResponseDto.builder()
                .refundId(refund.getId())
                .bookingId(refund.getBooking().getId())
                .amountCents(refund.getAmountCents())
                .currency(refund.getCurrency())
                .status(refund.getStatus())
                .preferVoucher(refund.getPreferVoucher())
                .gatewayRefundId(refund.getGatewayRefundId())
                .voucherCode(refund.getVoucherCode())
                .reason(refund.getReason())
                .processedAt(refund.getProcessedAt())
                .build();
    }

    private User resolveUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }
}
