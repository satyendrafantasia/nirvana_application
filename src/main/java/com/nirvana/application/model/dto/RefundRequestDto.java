package com.nirvana.application.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record RefundRequestDto(
        @Min(1) Integer amountCents,
        @Size(max = 500) String reason,
        Boolean preferVoucher
) {
}
