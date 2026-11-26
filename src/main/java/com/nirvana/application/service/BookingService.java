package com.nirvana.application.service;

import com.nirvana.application.model.dto.CancellationQuote;
import com.nirvana.application.model.dto.BookingCancelResponse;
import com.nirvana.application.model.dto.BookingCreateRequest;
import com.nirvana.application.model.dto.BookingCreateResponse;
import com.nirvana.application.model.dto.BookingListResponse;
import com.nirvana.application.model.dto.BookingRescheduleRequest;
import com.nirvana.application.model.dto.BookingRescheduleResponse;
import com.nirvana.application.model.enums.BookingStatus;
import org.springframework.data.domain.Pageable;

public interface BookingService {

    BookingCreateResponse createBooking(Long userId, BookingCreateRequest request);

    BookingListResponse listUserBookings(Long userId, BookingStatus status, Pageable pageable);

    CancellationQuote cancellationQuote(Long bookingId, Long userId);
    BookingCancelResponse cancelBooking(Long bookingId, Long userId, CancellationQuote accepted);
    default BookingCancelResponse cancelBooking(Long bookingId, Long userId) { return cancelBooking(bookingId, userId, null); }

    /**
     * Cancellation by the spa (or an administrator). The customer is refunded in full and gets
     * back any package session, whatever the customer cancellation policy says.
     */
    BookingCancelResponse cancelBookingBySpa(Long spaId, Long bookingId, String reason, boolean byAdmin);

    BookingRescheduleResponse rescheduleBooking(Long bookingId, Long userId, BookingRescheduleRequest request);

    /** The spa moves its own appointment, without the guest's reschedule limits. */
    BookingRescheduleResponse rescheduleBySpa(Long bookingId, Long spaId, BookingRescheduleRequest request);
}
