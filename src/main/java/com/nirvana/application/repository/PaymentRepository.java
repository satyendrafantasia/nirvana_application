package com.nirvana.application.repository;

import com.nirvana.application.model.Payment;
import com.nirvana.application.model.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByBookingId(Long bookingId);

    Optional<Payment> findTopByBookingIdOrderByCreatedAtDesc(Long bookingId);

    Optional<Payment> findFirstByBookingIdAndPaymentStatusIn(
            Long bookingId,
            List<PaymentStatus> statuses
    );

    Optional<Payment> findByBookingIdAndGateway(Long bookingId, String gateway);

    Optional<Payment> findByTransactionId(String transactionId);

    Optional<Payment> findByIntentId(String intentId);

    boolean existsByTransactionId(String transactionId);

    boolean existsByIntentId(String intentId);

    List<Payment> findByPaymentStatus(PaymentStatus status);


}
