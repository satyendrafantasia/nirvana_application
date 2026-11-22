// src/main/java/com/nirvana/application/model/dto/InvoiceResponseDto.java
package com.nirvana.application.model.dto;

import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record InvoiceResponseDto(
        Long id,
        String invoiceNumber,
        Long bookingId,
        String bookingReference,
        Long spaId,
        String spaName,
        Long userId,
        String userName,
        String userEmail,
        String userPhone,
        Long serviceId,
        String serviceName,

        Integer amountCents,
        Integer taxCents,
        Integer discountCents,
        Integer totalCents,
        Integer refundedCents,
        String currency,

        OffsetDateTime issuedAt,
        String pdfUrl,

        // For the printed invoice.
        String spaAddress,
        String spaPhone,
        String spaTimezone,
        OffsetDateTime appointmentStart,
        Integer durationMinutes,
        Integer guestCount,
        /** ONLINE or OFFLINE (pay at spa). */
        String paymentMode,
        String bookingStatus,
        String paymentStatus,
        /** The gateway's payment ID, such as Razorpay's pay_…, when paid online. */
        String paymentReference,
        OffsetDateTime paidAt
) {}
