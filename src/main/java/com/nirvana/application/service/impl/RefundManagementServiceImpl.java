package com.nirvana.application.service.impl;

import com.nirvana.application.model.*;
import com.nirvana.application.model.dto.*;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.PaymentStatus;
import com.nirvana.application.model.enums.RefundRequestStatus;
import com.nirvana.application.model.enums.RefundStatus;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.PaymentRefundRepository;
import com.nirvana.application.repository.PaymentRepository;
import com.nirvana.application.repository.UserRepository;
import com.nirvana.application.repository.VoucherRepository;
import com.nirvana.application.service.RefundManagementService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundManagementServiceImpl implements RefundManagementService {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final PaymentService paymentService;
    private final VoucherRepository voucherRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public RefundResponseDto requestRefund(Long bookingId, Long userId, RefundRequestDto request) {
        Booking booking = bookingRepository.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found for user"));

        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.NO_SHOW) {
            throw new IllegalStateException("Booking already cancelled or closed");
        }

        if (paymentRefundRepository.existsByBookingIdAndStatus(bookingId, RefundRequestStatus.REQUESTED)) {
            throw new IllegalStateException("Refund already requested for this booking");
        }

        Payment payment = paymentRepository
                .findFirstByBookingIdAndPaymentStatusIn(
                        bookingId,
                        List.of(PaymentStatus.CAPTURED, PaymentStatus.COMPLETED))
                .orElseThrow(() -> new IllegalStateException("No successful payment found for booking"));

        int cappedAmount = request.amountCents() != null
                ? Math.min(request.amountCents(), payment.getAmountCents())
                : payment.getAmountCents();

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
        PaymentRefund refund = paymentRefundRepository.findTopByBookingIdOrderByCreatedAtDesc(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("No refund request found for booking"));

        if (refund.getStatus() == RefundRequestStatus.COMPLETED) {
            return toDto(refund);
        }

        if (!request.approve()) {
            refund.setStatus(RefundRequestStatus.REJECTED);
            refund.setReason(request.reason());
            refund.setProcessedBy(resolveUser(adminUserId));
            refund.setProcessedAt(OffsetDateTime.now(ZoneOffset.UTC));
            PaymentRefund saved = paymentRefundRepository.save(refund);
            updateBookingRefundStatus(refund.getBooking(), RefundStatus.REJECTED);
            return toDto(saved);
        }

        refund.setStatus(RefundRequestStatus.PROCESSING);
        refund.setProcessedBy(resolveUser(adminUserId));
        refund = paymentRefundRepository.save(refund);

        if (Boolean.TRUE.equals(refund.getPreferVoucher()) || request.issueVoucher()) {
            Voucher voucher = issueVoucherInternal(refund.getBooking(), request.reason(), request.amountCents(), adminUserId);
            refund.setVoucherCode(voucher.getCode());
            refund.setStatus(RefundRequestStatus.COMPLETED);
            refund.setReason(request.reason());
            refund.setProcessedAt(OffsetDateTime.now(ZoneOffset.UTC));
            PaymentRefund saved = paymentRefundRepository.save(refund);
            updateBookingRefundStatus(refund.getBooking(), RefundStatus.COMPLETED);
            return toDto(saved);
        }

        int amountCents = request.amountCents() != null ? request.amountCents() : refund.getAmountCents();
        var gatewayRefund = paymentService.refundBookingPayment(bookingId, amountCents, request.reason());

        Payment payment = refund.getPayment();
        payment.setRefundedCents(payment.getRefundedCents() + amountCents);
        payment.setPaymentStatus(amountCents < payment.getAmountCents()
                ? PaymentStatus.PARTIALLY_REFUNDED
                : PaymentStatus.REFUNDED);
        paymentRepository.save(payment);

        refund.setGatewayRefundId(gatewayRefund.get("id"));
        refund.setStatus(RefundRequestStatus.COMPLETED);
        refund.setReason(request.reason());
        refund.setProcessedAt(OffsetDateTime.now(ZoneOffset.UTC));
        PaymentRefund saved = paymentRefundRepository.save(refund);

        updateBookingRefundStatus(refund.getBooking(), RefundStatus.COMPLETED);
        return toDto(saved);
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
