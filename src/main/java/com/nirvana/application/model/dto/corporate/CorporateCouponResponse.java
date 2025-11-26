package com.nirvana.application.model.dto.corporate;

import com.nirvana.application.model.enums.CorporateCouponStatus;
import com.nirvana.application.model.enums.CorporateCouponType;

import java.time.LocalDate;

public record CorporateCouponResponse(
        Long id,
        String corporateName,
        CorporateCouponType couponType,
        Integer totalSessions,
        Integer remainingSessions,
        String globalPackageType,
        LocalDate startDate,
        LocalDate expiryDate,
        CorporateCouponStatus status
) {
}
