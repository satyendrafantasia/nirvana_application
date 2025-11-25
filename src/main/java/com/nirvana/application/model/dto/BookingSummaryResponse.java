package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.PaymentMode;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
@Builder
public class BookingSummaryResponse {
    Long bookingId;
    String bookingReference;
    Long spaId;
    String spaName;
    String spaCity;
    String spaAddressLine;
    Long serviceId;
    String serviceName;
    Integer serviceDurationMinutes;
    Integer servicePriceCents;
    OffsetDateTime startTs;
    OffsetDateTime endTs;
    BookingStatus status;
    PaymentMode paymentMode;
    boolean canCancel;
    String cancellationReason;
}
