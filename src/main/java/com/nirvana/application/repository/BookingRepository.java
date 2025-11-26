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
