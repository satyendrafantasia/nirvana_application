package com.nirvana.application.model.dto.corporate;

import java.time.OffsetDateTime;

public record CorporateCouponUsageResponse(
        Long usageId,
        Long bookingId,
        Long spaId,
        OffsetDateTime usageDateTime,
        Integer sessionNumber,
        String notes
) {
}
