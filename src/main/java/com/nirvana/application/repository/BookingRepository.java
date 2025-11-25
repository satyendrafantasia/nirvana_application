package com.nirvana.application.repository;

// src/main/java/com/nirvana/application/repository/BookingRepository.java
import com.nirvana.application.model.Booking;
import com.nirvana.application.model.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsByBookingReference(String bookingReference);

    @EntityGraph(attributePaths = {"spa", "service"})
    Page<Booking> findWithDetailsByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"spa", "service"})
    Page<Booking> findWithDetailsByUserIdAndStatus(Long userId, BookingStatus status, Pageable pageable);

    Optional<Booking> findByIdAndUserId(Long bookingId, Long userId);
}
