package com.nirvana.application.service;

import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.dto.ProviderAnalyticsResponse;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProviderAnalyticsService {

    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;

    public ProviderAnalyticsResponse getSpaSummary(Long spaId) {
        ProviderAnalyticsResponse response = new ProviderAnalyticsResponse();
        response.setSpaId(spaId);
        response.setCompletedBookings(bookingRepository.countBySpaAndStatus(spaId, BookingStatus.COMPLETED));
        response.setCancelledBookings(bookingRepository.countBySpaAndStatus(spaId, BookingStatus.CANCELLED));
        response.setUpcomingBookings(bookingRepository.countBySpaAndStatus(spaId, BookingStatus.CONFIRMED));
        Long revenue = bookingRepository.sumGrossRevenueBySpa(spaId);
        response.setGrossRevenueCents(revenue != null ? revenue : 0L);
        Double averageRating = reviewRepository.averageVisibleRating(spaId);
        response.setAverageRating(averageRating != null ? (int) Math.round(averageRating) : null);
        return response;
    }
}
