package com.nirvana.application.repository;

import com.nirvana.application.model.PaymentRefund;
import com.nirvana.application.model.enums.RefundRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {
    Optional<PaymentRefund> findTopByBookingIdOrderByCreatedAtDesc(Long bookingId);

    @org.springframework.data.jpa.repository.Query("select r.id from PaymentRefund r where r.status = 'PROCESSING' and (r.nextCheckAt is null or r.nextCheckAt <= :now) order by r.nextCheckAt, r.id")
    java.util.List<Long> findDue(java.time.OffsetDateTime now, org.springframework.data.domain.Pageable limit);

    @org.springframework.data.jpa.repository.Query("select r.booking.id from PaymentRefund r where r.id = :id")
    Optional<Long> findBookingId(Long id);

    Optional<PaymentRefund> findByGatewayRefundId(String gatewayRefundId);

    org.springframework.data.domain.Slice<PaymentRefund> findByStatusInOrderByCreatedAtAscIdAsc(
            java.util.Collection<RefundRequestStatus> status, org.springframework.data.domain.Pageable page);

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(r.amountCents),0) from PaymentRefund r where r.payment.id = :paymentId and r.voucherCode is not null and r.status='COMPLETED'")
    int voucherTotal(Long paymentId);

    boolean existsByBookingIdAndStatusIn(Long bookingId, java.util.Collection<RefundRequestStatus> statuses);
}
