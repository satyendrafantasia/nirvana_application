// src/main/java/com/nirvana/application/model/dto/BookingResponse.java
package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.BookingStatus;

import java.time.OffsetDateTime;

public record BookingResponse(
        Long id,
        String bookingReference,
        Long spaId,
        Long serviceId,
        Long slotId,
        Long userId,
        BookingStatus status,

        Integer guestCount,
        Integer priceCents,
        Integer taxCents,
        Integer discountCents,
        Integer depositCents,
        String currency,

        OffsetDateTime startTs,
        OffsetDateTime endTs
) {}
