package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.PaymentMode;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
@Builder
public class ManagerBookingResponse {
    Long bookingId;
    String bookingReference;
    OffsetDateTime startTs;
    OffsetDateTime endTs;
    BookingStatus status;
    PaymentMode paymentMode;
    String serviceName;
    Integer priceCents;
    Integer taxCents;
    String customerName;
    String customerPhone;
    String therapistName;
    String therapistType;
}
