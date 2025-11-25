package com.nirvana.application.controller;

import com.nirvana.application.model.dto.ReviewCreateRequest;
import com.nirvana.application.model.dto.ReviewListResponse;
import com.nirvana.application.model.dto.ReviewResponse;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/reviews")
    public ReviewResponse createReview(@Valid @RequestBody ReviewCreateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return reviewService.createReview(userId, request);
    }

    @GetMapping("/spas/{spaId}/reviews")
    public ReviewListResponse listSpaReviews(
            @PathVariable Long spaId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return reviewService.listSpaReviews(spaId, pageable);
    }

    @GetMapping("/me/reviews")
    public ReviewListResponse listMyReviews(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        return reviewService.listUserReviews(userId, pageable);
    }
}
