package com.nirvana.application.model.dto.corporate;

import com.nirvana.application.model.enums.CorporateCouponType;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateCorporateDealRequest(
        @NotBlank String dealName,
        String description,
        @NotNull CorporateCouponType couponType,
        Integer totalSessionsPerEmployee,
        String globalPackageType,
        @FutureOrPresent LocalDate startDate,
        LocalDate endDate
) {}
