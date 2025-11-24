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
        String currency,

        OffsetDateTime issuedAt,
        String pdfUrl
) {}
