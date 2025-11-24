package com.nirvana.application.controller;

// src/main/java/com/nirvana/application/controller/BookingController.java
import com.nirvana.application.model.dto.BookingResponse;
import com.nirvana.application.model.dto.CreateBookingRequest;
import com.nirvana.application.service.impl.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/spas/{spaId}/services/{serviceId}/slots/{slotId}")
@RequiredArgsConstructor
@Validated
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/bookings")
    public BookingResponse createBooking(
            @PathVariable Long spaId,
            @PathVariable Long serviceId,
            @PathVariable Long slotId,
            @RequestParam Long userId, // in real prod: derive from SecurityContext
            @Valid @RequestBody CreateBookingRequest request
    ) {
        return bookingService.createBooking(
                spaId,
                serviceId,
                slotId,
                userId,
                request
        );
    }
}

