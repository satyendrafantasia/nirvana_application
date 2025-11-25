package com.nirvana.application.repository;

import com.nirvana.application.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByBookingId(Long bookingId);

    Page<Review> findBySpaIdAndIsVisibleTrue(Long spaId, Pageable pageable);

    Page<Review> findByUserId(Long userId, Pageable pageable);
}
