package com.nirvana.application.service.impl;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.dto.BookingAnalyticsResponse;
import com.nirvana.application.model.dto.ManagerBookingListResponse;
import com.nirvana.application.model.dto.ManagerBookingResponse;
import com.nirvana.application.model.dto.PagedResponse;
import com.nirvana.application.model.dto.RevenueBreakdown;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.BookingWindow;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.service.BookingManagerService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingManagerServiceImpl implements BookingManagerService {

    private final BookingRepository bookingRepository;
    private final SpaRepository spaRepository;

    @Override
    public ManagerBookingListResponse listBookings(Long spaId, BookingWindow window, Pageable pageable) {
        Spa spa = spaRepository.findById(spaId)
                .orElseThrow(() -> new EntityNotFoundException("Spa not found: " + spaId));

        DateRange range = resolveRange(spa, window);
        Page<Booking> page = (range.from() != null && range.to() != null)
                ? bookingRepository.findWithDetailsBySpaIdAndStartTsBetween(spaId, range.from(), range.to(), pageable)
                : bookingRepository.findWithDetailsBySpaId(spaId, pageable);

        List<ManagerBookingResponse> content = page.getContent().stream()
                .map(b -> ManagerBookingResponse.builder()
                        .bookingId(b.getId())
                        .bookingReference(b.getBookingReference())
                        .startTs(b.getStartTs())
                        .endTs(b.getEndTs())
                        .status(b.getStatus())
                        .paymentMode(b.getPaymentMode())
                        .serviceName(b.getService() != null ? b.getService().getName() : null)
                        .priceCents(b.getPriceCents())
                        .taxCents(b.getTaxCents())
                        .customerName(b.getUser() != null ? b.getUser().getName() : null)
                        .customerPhone(b.getUser() != null ? b.getUser().getPhone() : null)
                        .therapistName(b.getTherapist() != null ? b.getTherapist().getName() : null)
                        .therapistType(b.getTherapistType())
                        .build())
                .toList();

        PagedResponse<ManagerBookingResponse> response = new PagedResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );

        return new ManagerBookingListResponse(response);
    }

    @Override
    public BookingAnalyticsResponse getAnalytics(Long spaId, BookingWindow window) {
        Spa spa = spaRepository.findById(spaId)
                .orElseThrow(() -> new EntityNotFoundException("Spa not found: " + spaId));

        DateRange range = resolveRange(spa, window);
        ZoneId zoneId = resolveTimezone(spa);

        long totalBookings = bookingRepository.countBySpaAndRange(spaId, range.from(), range.to());
        long totalServed = bookingRepository.countBySpaStatusAndRange(spaId, BookingStatus.COMPLETED, range.from(), range.to());
        long totalCancelled = bookingRepository.countBySpaStatusAndRange(spaId, BookingStatus.CANCELLED, range.from(), range.to());

        List<RevenueBreakdown> revenue = buildRevenueBreakdown(spaId, range, zoneId);

        return BookingAnalyticsResponse.builder()
                .totalBookings(totalBookings)
                .totalServed(totalServed)
                .totalCancelled(totalCancelled)
                .dailyRevenue(filterRevenue(revenue, PeriodType.DAY))
                .weeklyRevenue(filterRevenue(revenue, PeriodType.WEEK))
                .monthlyRevenue(filterRevenue(revenue, PeriodType.MONTH))
                .build();
    }

    private List<RevenueBreakdown> buildRevenueBreakdown(Long spaId, DateRange range, ZoneId zoneId) {
        List<Object[]> raw = bookingRepository.findCompletedRevenueTimeline(spaId, range.from(), range.to());
        Map<String, Long> daily = new TreeMap<>();
        Map<String, Long> weekly = new TreeMap<>();
        Map<String, Long> monthly = new TreeMap<>();

        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM");

        for (Object[] row : raw) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            ZonedDateTime start = ((java.time.OffsetDateTime) row[0]).atZoneSameInstant(zoneId);
            long amount = ((Number) row[1]).longValue();

            LocalDate day = start.toLocalDate();
            LocalDate weekStart = day.with(DayOfWeek.MONDAY);
            YearMonth month = YearMonth.from(day);

            daily.merge(day.toString(), amount, Long::sum);
            weekly.merge(weekStart.toString(), amount, Long::sum);
            monthly.merge(month.format(monthFormatter), amount, Long::sum);
        }

        List<RevenueBreakdown> combined = new ArrayList<>();
        daily.forEach((k, v) -> combined.add(new RevenueBreakdown("DAY:" + k, v)));
        weekly.forEach((k, v) -> combined.add(new RevenueBreakdown("WEEK:" + k, v)));
        monthly.forEach((k, v) -> combined.add(new RevenueBreakdown("MONTH:" + k, v)));
        return combined;
    }

    private List<RevenueBreakdown> filterRevenue(List<RevenueBreakdown> combined, PeriodType type) {
        String prefix = type.name() + ":";
        return combined.stream()
                .filter(r -> r.getPeriod().startsWith(prefix))
                .map(r -> new RevenueBreakdown(r.getPeriod().substring(prefix.length()), r.getRevenueCents()))
                .collect(Collectors.toList());
    }

    private DateRange resolveRange(Spa spa, BookingWindow window) {
        ZoneId zoneId = resolveTimezone(spa);
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        if (window == null || window == BookingWindow.ALL) {
            return new DateRange(null, null);
        }
        LocalDate today = now.toLocalDate();
        LocalDate startDate;
        switch (window) {
            case TODAY -> startDate = today;
            case LAST_7_DAYS -> startDate = today.minusDays(6);
            case LAST_30_DAYS -> startDate = today.minusDays(29);
            default -> startDate = today;
        }
        ZonedDateTime start = startDate.atStartOfDay(zoneId);
        ZonedDateTime end = today.plusDays(1).atStartOfDay(zoneId);
        return new DateRange(start.toOffsetDateTime(), end.toOffsetDateTime());
    }

    private ZoneId resolveTimezone(Spa spa) {
        if (spa.getAddress() != null && spa.getAddress().getTimezone() != null && !spa.getAddress().getTimezone().isBlank()) {
            return ZoneId.of(spa.getAddress().getTimezone());
        }
        if (spa.getTimezone() != null && !spa.getTimezone().isBlank()) {
            return ZoneId.of(spa.getTimezone());
        }
        return ZoneId.of("UTC");
    }

    private enum PeriodType {
        DAY, WEEK, MONTH
    }

    private record DateRange(java.time.OffsetDateTime from, java.time.OffsetDateTime to) {
    }
}
