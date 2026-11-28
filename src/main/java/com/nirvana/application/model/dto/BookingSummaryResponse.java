package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.PaymentMode;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
@Builder
public class BookingSummaryResponse {
    String originalPreference;
    String therapistName;
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
    Integer payAtSpaCheckoutCents;
    String currency;
    OffsetDateTime startTs;
    OffsetDateTime endTs;
    BookingStatus status;
    boolean spaAcknowledged;
    String refundStatus;
    PaymentMode paymentMode;
    boolean invoiceAvailable;
    boolean canCancel;
    String cancellationReason;
    /** The add-ons on this booking, as they were sold; empty when none. */
    java.util.List<com.nirvana.application.service.addons.BookingAddOns.Line> addOns;
}
