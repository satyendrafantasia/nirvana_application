// src/main/java/com/nirvana/application/model/dto/PaymentInitResponse.java
package com.nirvana.application.model.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Value
@Builder
public class PaymentInitResponse {
    Long bookingId;
    String bookingReference;

    String razorpayKeyId;   // public key for checkout
    String razorpayOrderId; // order_xxx
    String idempotencyKey;
    BigDecimal amount;      // in major unit (₹)
    String currency;        // "INR"
    String description;

    String customerName;
    String customerEmail;
    String customerPhone;

    OffsetDateTime expiresAt;

    String receiptUrl;
}
