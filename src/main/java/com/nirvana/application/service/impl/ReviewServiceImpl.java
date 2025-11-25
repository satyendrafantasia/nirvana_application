package com.nirvana.application.service.impl;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.Review;
import com.nirvana.application.model.dto.PagedResponse;
import com.nirvana.application.model.dto.ReviewCreateRequest;
import com.nirvana.application.model.dto.ReviewListResponse;
import com.nirvana.application.model.dto.ReviewResponse;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.ReviewRepository;
import com.nirvana.application.service.ReviewService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;

    @Override
    @Transactional(readOnly = true)
    public ReviewListResponse listSpaReviews(Long spaId, Pageable pageable) {
        Page<Review> page = reviewRepository.findBySpaIdAndIsVisibleTrue(spaId, pageable);
        return buildPagedResponse(page);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewListResponse listUserReviews(Long userId, Pageable pageable) {
        Page<Review> page = reviewRepository.findByUserId(userId, pageable);
        return buildPagedResponse(page);
    }

    @Override
    @Transactional
    public ReviewResponse createReview(Long userId, ReviewCreateRequest request) {
        Booking booking = bookingRepository.findByIdAndUserId(request.getBookingId(), userId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found for user"));

        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new IllegalStateException("Reviews can only be created after the booking is completed");
        }

        if (reviewRepository.existsByBookingId(booking.getId())) {
            throw new IllegalStateException("Review already submitted for this booking");
        }

        Review review = new Review();
        review.setBooking(booking);
        review.setUser(booking.getUser());
        review.setSpa(booking.getSpa());
        review.setRating(request.getRating());
        review.setTitle(request.getTitle());
        review.setText(request.getText());
        review.setMetaJson(request.getMetaJson());
        review.setCreatedAt(OffsetDateTime.now());

        Review saved = reviewRepository.save(review);
        return toResponse(saved);
    }

    private ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getBooking().getId(),
                review.getSpa().getId(),
                review.getUser().getId(),
                review.getRating(),
                review.getTitle(),
                review.getText(),
                review.getIsVisible(),
                review.getCreatedAt()
        );
    }

    private ReviewListResponse buildPagedResponse(Page<Review> page) {
        List<ReviewResponse> content = page.stream()
                .map(this::toResponse)
                .toList();

        PagedResponse<ReviewResponse> response = new PagedResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
        return new ReviewListResponse(response);
    }
}
