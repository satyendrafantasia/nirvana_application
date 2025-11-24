// src/main/java/com/nirvana/application/model/dto/InvoiceEmailDto.java
package com.nirvana.application.model.dto;

import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record InvoiceEmailDto(
        String invoiceNumber,
        OffsetDateTime issuedAt,

        String toEmail,
        String toName,

        String spaName,
        String spaAddressLine1,
        String spaAddressLine2,
        String spaCity,
        String spaState,
        String spaCountry,
        String spaPostalCode,
        String spaPhone,

        String bookingReference,
        OffsetDateTime bookingStartTs,
        OffsetDateTime bookingEndTs,
        String serviceName,
        Integer guestCount,

        Integer amountCents,
        Integer taxCents,
        Integer discountCents,
        Integer totalCents,
        String currency,

        String invoicePdfUrl
) {}
