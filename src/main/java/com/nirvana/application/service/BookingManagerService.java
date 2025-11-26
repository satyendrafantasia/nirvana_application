package com.nirvana.application.service;

import com.nirvana.application.model.dto.BookingAnalyticsResponse;
import com.nirvana.application.model.dto.ManagerBookingListResponse;
import com.nirvana.application.model.enums.BookingWindow;
import org.springframework.data.domain.Pageable;

public interface BookingManagerService {

    ManagerBookingListResponse listBookings(Long spaId, BookingWindow window, Pageable pageable);

    BookingAnalyticsResponse getAnalytics(Long spaId, BookingWindow window);
}
