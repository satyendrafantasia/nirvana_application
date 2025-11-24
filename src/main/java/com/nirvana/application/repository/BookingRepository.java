package com.nirvana.application.repository;

// src/main/java/com/nirvana/application/repository/BookingRepository.java
import com.nirvana.application.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsByBookingReference(String bookingReference);
}

