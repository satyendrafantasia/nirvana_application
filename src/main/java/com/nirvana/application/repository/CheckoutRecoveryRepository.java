package com.nirvana.application.repository;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.CheckoutRecovery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface CheckoutRecoveryRepository extends JpaRepository<CheckoutRecovery, Long> {

    /**
     * Online checkouts started inside the window that were left unpaid: still waiting for payment,
     * or cancelled by the system because payment never arrived. Newest first, and only those
     * without a reminder yet. Every other rule is checked again under a lock before sending.
     */
    @Query("""
            select b.id from Booking b
            where b.createdAt <= :leftBefore and b.createdAt >= :leftAfter
              and b.startTs > :startsAfter
              and b.paymentMode = com.nirvana.application.model.enums.PaymentMode.ONLINE
              and (b.status = com.nirvana.application.model.enums.BookingStatus.PENDING_PAYMENT
                   or (b.status = com.nirvana.application.model.enums.BookingStatus.CANCELLED
                       and b.cancelledBy = com.nirvana.application.model.enums.CancellationActor.SYSTEM
                       and b.cancellationReasonCode = 'PAYMENT_TIMEOUT'))
              and not exists (select r.id from CheckoutRecovery r where r.bookingId = b.id)
            order by b.createdAt desc
            """)
    List<Long> findAbandonedCheckoutIds(@Param("leftBefore") OffsetDateTime leftBefore,
                                        @Param("leftAfter") OffsetDateTime leftAfter,
                                        @Param("startsAfter") OffsetDateTime startsAfter,
                                        Pageable page);

    @Query(value = "SELECT group_id FROM booking_group_member WHERE booking_id = :id", nativeQuery = true)
    Optional<Long> findGroupForBooking(@Param("id") long id);

    boolean existsByBookingId(Long bookingId);

    long countByUserIdAndSentAtAfter(Long userId, OffsetDateTime since);

    Optional<CheckoutRecovery> findByIdAndTokenNonce(Long id, String tokenNonce);

    List<CheckoutRecovery> findByConvertedAtIsNullAndStatusAndSentAtAfter(CheckoutRecovery.Status status, OffsetDateTime since);

    /** Did the guest book (or pay for) anything after leaving this checkout? */
    @Query("""
            select count(b) from Booking b
            where b.user.id = :userId and b.id <> :bookingId and b.createdAt > :since
              and b.status in (com.nirvana.application.model.enums.BookingStatus.PENDING_PAYMENT,
                               com.nirvana.application.model.enums.BookingStatus.CONFIRMED,
                               com.nirvana.application.model.enums.BookingStatus.ARRIVED,
                               com.nirvana.application.model.enums.BookingStatus.COMPLETED,
                               com.nirvana.application.model.enums.BookingStatus.NO_SHOW)
            """)
    long countBookedSince(@Param("userId") Long userId, @Param("bookingId") Long bookingId, @Param("since") OffsetDateTime since);

    /** The first booking at the same spa, made after the reminder went out, that was confirmed. */
    @Query("""
            select b from Booking b
            where b.user.id = :userId and b.spa.id = :spaId and b.createdAt > :since and b.id <> :abandonedId
              and b.status in (com.nirvana.application.model.enums.BookingStatus.CONFIRMED,
                               com.nirvana.application.model.enums.BookingStatus.ARRIVED,
                               com.nirvana.application.model.enums.BookingStatus.COMPLETED,
                               com.nirvana.application.model.enums.BookingStatus.NO_SHOW)
            order by b.createdAt asc
            """)
    List<Booking> findConversions(@Param("userId") Long userId, @Param("spaId") Long spaId,
                                  @Param("since") OffsetDateTime since, @Param("abandonedId") Long abandonedId,
                                  Pageable page);

    interface Totals {
        Long getSent();
        Long getFailed();
        Long getClicked();
        Long getConverted();
        Long getRecoveredCents();
    }

    @Query("""
            select sum(case when r.status = com.nirvana.application.model.CheckoutRecovery.Status.SENT then 1 else 0 end) as sent,
                   sum(case when r.status = com.nirvana.application.model.CheckoutRecovery.Status.FAILED then 1 else 0 end) as failed,
                   sum(case when r.clickedAt is not null then 1 else 0 end) as clicked,
                   sum(case when r.convertedAt is not null then 1 else 0 end) as converted,
                   sum(coalesce(r.recoveredCents, 0)) as recoveredCents
            from CheckoutRecovery r
            where r.sentAt >= :from and r.sentAt < :to and (:spaId is null or r.spaId = :spaId)
            """)
    Totals totals(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to, @Param("spaId") Long spaId);
}
