package com.nirvana.application.model.dto.corporate;

import com.nirvana.application.model.enums.CorporateOnboardingStatus;
import java.time.OffsetDateTime;

public record CorporateOnboardingUploadStatusResponse(
        Long id,
        Long corporateId,
        Long corporateDealId,
        String originalFileName,
        CorporateOnboardingStatus status,
        Integer totalRecords,
        Integer successCount,
        Integer failureCount,
        OffsetDateTime createdAt
) {
}
