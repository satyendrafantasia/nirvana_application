package com.nirvana.application.model.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Value
@Builder
public class UpiPaymentInitResponse {
    Long bookingId;
    String bookingReference;
    BigDecimal amount;
    String currency;
    String payeeName;
    String upiId;
    String upiDeepLink;
    String qrImageUrl;
    String transactionRef;
    OffsetDateTime expiresAt;
}
