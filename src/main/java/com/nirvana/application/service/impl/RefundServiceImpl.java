package com.nirvana.application.service.impl;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.Payment;
import com.nirvana.application.model.enums.PaymentStatus;
import com.nirvana.application.model.enums.RefundRoute;
import com.nirvana.application.model.enums.RefundStatus;
import com.nirvana.application.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundServiceImpl implements RefundService {

    private final PaymentService paymentService;

    @Override
    @Transactional
    public void processRefund(Payment payment, Booking booking, RefundRoute route, Integer refundAmountCents) {
        if (payment == null || booking == null) {
            throw new IllegalArgumentException("Payment and booking are required for refund");
        }
        if (payment.getPaymentStatus() != PaymentStatus.CAPTURED
                && payment.getPaymentStatus() != PaymentStatus.COMPLETED) {
            log.info("Skipping refund because payment not successful for booking {}", booking.getId());
            return;
        }

        if (route == RefundRoute.WALLET_CREDIT) {
            log.info("Queueing wallet credit for booking {} amount {}", booking.getId(), refundAmountCents);
            booking.setRefundStatus(RefundStatus.PROCESSING);
            return;
        }

        try {
            paymentService.refundBookingPayment(booking.getId(), refundAmountCents, "User cancelled booking");
        } catch (Exception ex) {
            log.error("Failed to refund payment for booking {}", booking.getId(), ex);
            throw ex;
        }
    }
}
