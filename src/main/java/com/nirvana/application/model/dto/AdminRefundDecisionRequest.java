package com.nirvana.application.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminRefundDecisionRequest(
        @Min(1) Integer amountCents,
        boolean approve,
        boolean issueVoucher,
        @NotBlank @Size(max = 500) String reason
) {
}
