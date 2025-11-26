package com.nirvana.application.model.dto.corporate;

import com.nirvana.application.model.enums.CorporateCouponType;
import com.nirvana.application.model.enums.CorporateDealStatus;
import com.nirvana.application.model.enums.CorporatePaymentStatus;

import java.time.LocalDate;

public record CorporateDealResponse(
        Long id,
        Long corporateId,
        String dealName,
        String description,
        CorporateCouponType couponType,
        Integer totalSessionsPerEmployee,
        String globalPackageType,
        LocalDate startDate,
        LocalDate endDate,
        CorporateDealStatus status,
        CorporatePaymentStatus corporatePaymentStatus
) {
}
