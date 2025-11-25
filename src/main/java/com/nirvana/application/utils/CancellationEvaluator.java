package com.nirvana.application.utils;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.enums.BookingStatus;
import lombok.experimental.UtilityClass;

import java.time.OffsetDateTime;

@UtilityClass
public class CancellationEvaluator {

    public CancellationEligibility evaluateCancellation(Booking booking, OffsetDateTime now, int cutoffMinutes) {
        if (booking == null) {
            return CancellationEligibility.denied("Booking not found");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return CancellationEligibility.denied("Booking already cancelled");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            return CancellationEligibility.denied("Service already completed");
        }
        if (booking.getStatus() == BookingStatus.NO_SHOW) {
            return CancellationEligibility.denied("Marked as no-show");
        }
        OffsetDateTime start = booking.getStartTs();
        OffsetDateTime end = booking.getEndTs();

        if (start == null || end == null) {
            return CancellationEligibility.denied("Booking time not available");
        }
        if (!end.isAfter(start)) {
            return CancellationEligibility.denied("Invalid booking duration");
        }

        if (!now.isBefore(end)) {
            return CancellationEligibility.denied("Service already completed");
        }
        if (!now.isBefore(start)) {
            return CancellationEligibility.denied("Service already started");
        }
        if (now.plusMinutes(cutoffMinutes).isAfter(start)) {
            return CancellationEligibility.denied("Within " + cutoffMinutes + "-minute cutoff window");
        }
        return CancellationEligibility.allowed();
    }
}
