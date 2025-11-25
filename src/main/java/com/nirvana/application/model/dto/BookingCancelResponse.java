package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.RefundStatus;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BookingCancelResponse {
    Long bookingId;
    String bookingReference;
    BookingStatus status;
    boolean refundInitiated;
    Integer refundAmountCents;
    RefundStatus refundStatus;
}
