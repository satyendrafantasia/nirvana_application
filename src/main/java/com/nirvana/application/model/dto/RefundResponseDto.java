package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.RefundRequestStatus;
import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record RefundResponseDto(
        Long refundId,
        Long bookingId,
        Integer amountCents,
        String currency,
        RefundRequestStatus status,
        Boolean preferVoucher,
        String gatewayRefundId,
        String voucherCode,
        String reason,
        OffsetDateTime processedAt
) {
}
