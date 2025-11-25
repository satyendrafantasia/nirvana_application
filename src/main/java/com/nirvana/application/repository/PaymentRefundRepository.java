package com.nirvana.application.repository;

import com.nirvana.application.model.PaymentRefund;
import com.nirvana.application.model.enums.RefundRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {
    Optional<PaymentRefund> findTopByBookingIdOrderByCreatedAtDesc(Long bookingId);

    boolean existsByBookingIdAndStatus(Long bookingId, RefundRequestStatus status);
}
