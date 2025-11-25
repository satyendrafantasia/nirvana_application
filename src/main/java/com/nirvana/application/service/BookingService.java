package com.nirvana.application.service;

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

    BookingCancelResponse cancelBooking(Long bookingId, Long userId);

    BookingRescheduleResponse rescheduleBooking(Long bookingId, Long userId, BookingRescheduleRequest request);
}
