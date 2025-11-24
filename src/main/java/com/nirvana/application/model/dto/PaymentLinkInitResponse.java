// src/main/java/com/nirvana/application/model/dto/PaymentLinkInitResponse.java
package com.nirvana.application.model.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Builder
public record PaymentLinkInitResponse(
        Long bookingId,
        String bookingReference,

        String razorpayPaymentLinkId,   // plink_xxx
        String shortUrl,
        String status,

        BigDecimal amount,
        String currency,
        OffsetDateTime expireBy
) {}
