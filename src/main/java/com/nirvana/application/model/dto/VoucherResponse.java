package com.nirvana.application.model.dto;

import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record VoucherResponse(
        String code,
        Integer amountCents,
        String currency,
        String note,
        OffsetDateTime expiresAt,
        OffsetDateTime issuedAt
) {
}
