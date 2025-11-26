package com.nirvana.application.controller;

import com.nirvana.application.model.dto.BookingAnalyticsResponse;
import com.nirvana.application.model.dto.ManagerBookingListResponse;
import com.nirvana.application.model.enums.BookingWindow;
import com.nirvana.application.service.BookingManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/spas/{spaId}/bookings")
@RequiredArgsConstructor
public class BookingManagerController {

    private final BookingManagerService bookingManagerService;

    @GetMapping
    public ManagerBookingListResponse listBookings(@PathVariable Long spaId,
                                                   @RequestParam(value = "window", required = false) BookingWindow window,
                                                   @RequestParam(value = "page", defaultValue = "0") int page,
                                                   @RequestParam(value = "size", defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return bookingManagerService.listBookings(spaId, window, pageable);
    }

    @GetMapping("/analytics")
    public BookingAnalyticsResponse analytics(@PathVariable Long spaId,
                                              @RequestParam(value = "window", required = false) BookingWindow window) {
        return bookingManagerService.getAnalytics(spaId, window);
    }
}
