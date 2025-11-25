package com.nirvana.application.service;

import com.nirvana.application.model.dto.ReviewCreateRequest;
import com.nirvana.application.model.dto.ReviewListResponse;

import org.springframework.data.domain.Pageable;

public interface ReviewService {

    ReviewListResponse listSpaReviews(Long spaId, Pageable pageable);

    ReviewListResponse listUserReviews(Long userId, Pageable pageable);

    com.nirvana.application.model.dto.ReviewResponse createReview(Long userId, ReviewCreateRequest request);
}
