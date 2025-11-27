package com.nirvana.application.controller;

import com.nirvana.application.model.dto.BookingCancelResponse;
import com.nirvana.application.model.dto.BookingCreateRequest;
import com.nirvana.application.model.dto.BookingCreateResponse;
import com.nirvana.application.model.dto.BookingListResponse;
import com.nirvana.application.model.dto.BookingRescheduleRequest;
import com.nirvana.application.model.dto.BookingRescheduleResponse;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Booking", description = "Booking lifecycle including creation, reschedule and cancellation")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/bookings")
    @Operation(
            summary = "Create booking",
            description = "Create a booking for the authenticated user for the requested spa service, including handling package or coupon usage."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Booking created", content = @Content(mediaType = "application/json", schema = @Schema(implementation = BookingCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = com.nirvana.application.api.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Slot not available", content = @Content(schema = @Schema(implementation = com.nirvana.application.api.ApiErrorResponse.class)))
    })
    public BookingCreateResponse createBooking(
            @Valid @RequestBody BookingCreateRequest request
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        return bookingService.createBooking(userId, request);
    }

    @GetMapping("/me/bookings")
    @Operation(
            summary = "List my bookings",
            description = "Retrieve paginated bookings for the authenticated user with optional status filter."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bookings fetched", content = @Content(mediaType = "application/json", schema = @Schema(implementation = BookingListResponse.class)))
    })
    public BookingListResponse listMyBookings(
            @Parameter(description = "Filter by booking status") @RequestParam(value = "status", required = false) BookingStatus status,
            @Parameter(description = "Page number", example = "0") @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20") @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        return bookingService.listUserBookings(userId, status, pageable);
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    @Operation(
            summary = "Cancel booking",
            description = "Cancel a future booking for the authenticated user subject to business rules (cut-off windows, consumption status)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking cancelled", content = @Content(mediaType = "application/json", schema = @Schema(implementation = BookingCancelResponse.class))),
            @ApiResponse(responseCode = "404", description = "Booking not found", content = @Content(schema = @Schema(implementation = com.nirvana.application.api.ApiErrorResponse.class)))
    })
    public BookingCancelResponse cancelBooking(@Parameter(description = "Identifier of the booking") @PathVariable Long bookingId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return bookingService.cancelBooking(bookingId, userId);
    }

    @PostMapping("/bookings/{bookingId}/reschedule")
    @Operation(
            summary = "Reschedule booking",
            description = "Reschedule a confirmed booking into another available slot while retaining payments and coupon usage rules."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking rescheduled", content = @Content(mediaType = "application/json", schema = @Schema(implementation = BookingRescheduleResponse.class))),
            @ApiResponse(responseCode = "409", description = "Slot conflict or reschedule not allowed", content = @Content(schema = @Schema(implementation = com.nirvana.application.api.ApiErrorResponse.class)))
    })
    public BookingRescheduleResponse rescheduleBooking(
            @Parameter(description = "Identifier of the booking") @PathVariable Long bookingId,
            @Valid @RequestBody BookingRescheduleRequest request
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        return bookingService.rescheduleBooking(bookingId, userId, request);
    }
}
