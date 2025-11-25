package com.nirvana.application.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record VoucherIssueRequest(
        @NotNull @Min(1) Integer amountCents,
        String currency,
        @Size(max = 500) String note,
        OffsetDateTime expiresAt
) {
}
