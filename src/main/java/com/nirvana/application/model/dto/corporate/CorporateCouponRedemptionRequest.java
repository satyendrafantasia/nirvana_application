package com.nirvana.application.model.dto.corporate;

import jakarta.validation.constraints.NotNull;

public record CorporateCouponRedemptionRequest(
        @NotNull Long spaId,
        @NotNull Long bookingId,
        Long couponId
) {
}
