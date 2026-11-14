package com.nirvana.application.repository;

// src/main/java/com/nirvana/application/repository/BookingRepository.java
import com.nirvana.application.model.Booking;
import com.nirvana.application.model.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id and b.user.id = :userId")
    Optional<Booking> findOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    @Query("""
        select count(b)>0 from Booking b where b.therapist.id=:id and (:exclude is null or b.id<>:exclude)
        and b.status not in (com.nirvana.application.model.enums.BookingStatus.CANCELLED,com.nirvana.application.model.enums.BookingStatus.NO_SHOW)
        and b.startTs < :end and b.endTs > :start
        """)
    boolean hasOtherAssignment(@Param("id") Long id,@Param("start") OffsetDateTime start,
                               @Param("end") OffsetDateTime end,@Param("exclude") Long exclude);

    @Query("select b.slot.id, b.id from Booking b where b.slot.id in :slots and b.status in :statuses order by b.id")
    List<Object[]> commitmentIds(@Param("slots") List<Long> slots, @Param("statuses") List<BookingStatus> statuses);

    List<Booking> findBySlot_IdAndStatusIn(Long slotId, List<BookingStatus> statuses);

    @Query("select distinct b.service.id from Booking b where b.therapist.id=:therapist and b.endTs>:now and b.status in :statuses")
    List<Long> committedServices(@Param("therapist") Long therapist, @Param("now") OffsetDateTime now,
                                 @Param("statuses") List<BookingStatus> statuses);

    /** Unpaid online bookings that are past the payment window, or whose appointment has already started. */
    @Query("""
            select b.id from Booking b
            where b.status = com.nirvana.application.model.enums.BookingStatus.PENDING_PAYMENT
              and (b.createdAt < :createdBefore or b.startTs < :now)
            order by b.id
            """)
    List<Long> findExpirablePendingPaymentIds(@Param("createdBefore") OffsetDateTime createdBefore,
                                              @Param("now") OffsetDateTime now);

    /** Appointments that ended before the cutoff and were never closed out by the spa. */
    @Query("""
            select b.id from Booking b
            where b.status in (com.nirvana.application.model.enums.BookingStatus.CONFIRMED,
                               com.nirvana.application.model.enums.BookingStatus.ARRIVED)
              and b.endTs < :endedBefore
            order by b.id
            """)
    List<Long> findAutoCompletableIds(@Param("endedBefore") OffsetDateTime endedBefore);

    /** Confirmed, not yet started appointments inside the reminder window that have no reminder yet. */
    @Query("""
            select b.id from Booking b
            where b.status = com.nirvana.application.model.enums.BookingStatus.CONFIRMED
              and b.reminderProcessedAt is null
              and b.startTs > :now and b.startTs <= :until
            order by b.startTs
            """)
    List<Long> findReminderDueIds(@Param("now") OffsetDateTime now, @Param("until") OffsetDateTime until);

    /** Bookings holding a use of the coupon. Cancelled bookings give their use back. */
    @Query("""
            select count(b) from Booking b
            where b.coupon.id = :couponId
              and b.status <> com.nirvana.application.model.enums.BookingStatus.CANCELLED
            """)
    long countCouponUses(@Param("couponId") Long couponId);

    @Query("""
            select count(b) from Booking b
            where b.coupon.id = :couponId and b.user.id = :userId
              and b.status <> com.nirvana.application.model.enums.BookingStatus.CANCELLED
            """)
    long countCouponUsesByUser(@Param("couponId") Long couponId, @Param("userId") Long userId);

    boolean existsByBookingReference(String bookingReference);

    @EntityGraph(attributePaths = {"spa", "service"})
    Page<Booking> findWithDetailsByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"spa", "service"})
    Page<Booking> findWithDetailsByUserIdAndStatus(Long userId, BookingStatus status, Pageable pageable);

    Optional<Booking> findByIdAndUserId(Long bookingId, Long userId);

    @EntityGraph(attributePaths = {"user", "service", "therapist"})
    Page<Booking> findWithDetailsBySpaId(Long spaId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "service", "therapist"})
    Page<Booking> findWithDetailsBySpaIdAndStartTsBetween(Long spaId, OffsetDateTime from, OffsetDateTime to, Pageable pageable);

    @Query("select count(b) from Booking b where b.spa.id = :spaId and b.status = :status")
    long countBySpaAndStatus(@Param("spaId") Long spaId, @Param("status") BookingStatus status);

    @Query("select sum(b.priceCents + b.taxCents) from Booking b where b.spa.id = :spaId and b.status = 'COMPLETED'")
    Long sumGrossRevenueBySpa(@Param("spaId") Long spaId);

    @Query("""
            select count(b) from Booking b
            where b.spa.id = :spaId
              and (:from is null or b.startTs >= :from)
              and (:to is null or b.startTs < :to)
            """)
    long countBySpaAndRange(@Param("spaId") Long spaId,
                            @Param("from") OffsetDateTime from,
                            @Param("to") OffsetDateTime to);

    @Query("""
            select count(b) from Booking b
            where b.spa.id = :spaId
              and b.status = :status
              and (:from is null or b.startTs >= :from)
              and (:to is null or b.startTs < :to)
            """)
    long countBySpaStatusAndRange(@Param("spaId") Long spaId,
                                  @Param("status") BookingStatus status,
                                  @Param("from") OffsetDateTime from,
                                  @Param("to") OffsetDateTime to);

    @Query("""
            select b.startTs, (b.priceCents + b.taxCents) as revenue
            from Booking b
            where b.spa.id = :spaId
              and b.status = 'COMPLETED'
              and (:from is null or b.startTs >= :from)
              and (:to is null or b.startTs < :to)
            """)
    List<Object[]> findCompletedRevenueTimeline(@Param("spaId") Long spaId,
                                                @Param("from") OffsetDateTime from,
                                                @Param("to") OffsetDateTime to);

    @Query("""
            SELECT DISTINCT b.therapist.id, b.startTs, b.endTs
            FROM Booking b
            WHERE b.therapist.id IN :therapistIds
              AND b.status NOT IN (com.nirvana.application.model.enums.BookingStatus.CANCELLED, com.nirvana.application.model.enums.BookingStatus.NO_SHOW)
              AND b.startTs < :rangeEnd AND b.endTs > :rangeStart
            """)
    List<Object[]> findActiveTherapistBookings(
            @Param("therapistIds") List<Long> therapistIds,
            @Param("rangeStart") OffsetDateTime rangeStart,
            @Param("rangeEnd") OffsetDateTime rangeEnd);

    @Query("""
            SELECT COUNT(b) > 0 FROM Booking b
            WHERE b.therapist.id = :therapistId
              AND b.status NOT IN (com.nirvana.application.model.enums.BookingStatus.CANCELLED, com.nirvana.application.model.enums.BookingStatus.NO_SHOW)
              AND b.startTs < :slotEnd AND b.endTs > :slotStart
            """)
    boolean existsActiveTherapistConflict(
            @Param("therapistId") Long therapistId,
            @Param("slotStart") OffsetDateTime slotStart,
            @Param("slotEnd") OffsetDateTime slotEnd);
}
