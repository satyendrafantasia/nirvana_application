package com.nirvana.application.model.dto;

public class ProviderAnalyticsResponse {
    private Long spaId;
    private Long completedBookings;
    private Long cancelledBookings;
    private Long upcomingBookings;
    private Long grossRevenueCents;
    private Integer averageRating;

    public Long getSpaId() {
        return spaId;
    }

    public void setSpaId(Long spaId) {
        this.spaId = spaId;
    }

    public Long getCompletedBookings() {
        return completedBookings;
    }

    public void setCompletedBookings(Long completedBookings) {
        this.completedBookings = completedBookings;
    }

    public Long getCancelledBookings() {
        return cancelledBookings;
    }

    public void setCancelledBookings(Long cancelledBookings) {
        this.cancelledBookings = cancelledBookings;
    }

    public Long getUpcomingBookings() {
        return upcomingBookings;
    }

    public void setUpcomingBookings(Long upcomingBookings) {
        this.upcomingBookings = upcomingBookings;
    }

    public Long getGrossRevenueCents() {
        return grossRevenueCents;
    }

    public void setGrossRevenueCents(Long grossRevenueCents) {
        this.grossRevenueCents = grossRevenueCents;
    }

    public Integer getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(Integer averageRating) {
        this.averageRating = averageRating;
    }
}
