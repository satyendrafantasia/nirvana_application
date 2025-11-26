package com.nirvana.application.model.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class BookingAnalyticsResponse {
    long totalBookings;
    long totalServed;
    long totalCancelled;
    List<RevenueBreakdown> dailyRevenue;
    List<RevenueBreakdown> weeklyRevenue;
    List<RevenueBreakdown> monthlyRevenue;
}
