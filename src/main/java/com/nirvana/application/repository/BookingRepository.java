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

import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsByBookingReference(String bookingReference);

    @EntityGraph(attributePaths = {"spa", "service"})
    Page<Booking> findWithDetailsByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"spa", "service"})
    Page<Booking> findWithDetailsByUserIdAndStatus(Long userId, BookingStatus status, Pageable pageable);

    Optional<Booking> findByIdAndUserId(Long bookingId, Long userId);

    @Query("select count(b) from Booking b where b.spa.id = :spaId and b.status = :status")
    long countBySpaAndStatus(@Param("spaId") Long spaId, @Param("status") BookingStatus status);

    @Query("select sum(b.priceCents + b.taxCents) from Booking b where b.spa.id = :spaId and b.status = 'COMPLETED'")
    Long sumGrossRevenueBySpa(@Param("spaId") Long spaId);
}
