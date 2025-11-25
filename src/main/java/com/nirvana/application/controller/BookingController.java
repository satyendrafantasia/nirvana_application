package com.nirvana.application.controller;

import com.nirvana.application.model.dto.BookingCancelResponse;
import com.nirvana.application.model.dto.BookingCreateRequest;
import com.nirvana.application.model.dto.BookingCreateResponse;
import com.nirvana.application.model.dto.BookingListResponse;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.BookingService;
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
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/bookings")
    public BookingCreateResponse createBooking(
            @Valid @RequestBody BookingCreateRequest request
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        return bookingService.createBooking(userId, request);
    }

    @GetMapping("/me/bookings")
    public BookingListResponse listMyBookings(
            @RequestParam(value = "status", required = false) BookingStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        return bookingService.listUserBookings(userId, status, pageable);
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    public BookingCancelResponse cancelBooking(@PathVariable Long bookingId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return bookingService.cancelBooking(bookingId, userId);
    }
}
