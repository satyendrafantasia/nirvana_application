package com.nirvana.application.repository;

import com.nirvana.application.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByBookingId(Long bookingId);

    Page<Review> findBySpaIdAndIsVisibleTrue(Long spaId, Pageable pageable);

    Page<Review> findByUserId(Long userId, Pageable pageable);

    @Query("select avg(r.rating) from Review r where r.spa.id = :spaId and r.isVisible = true")
    Double averageVisibleRating(@Param("spaId") Long spaId);
}
