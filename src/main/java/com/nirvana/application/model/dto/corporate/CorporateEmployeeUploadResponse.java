package com.nirvana.application.model.dto.corporate;

import java.util.List;

public record CorporateEmployeeUploadResponse(
        Long uploadId,
        int totalRecords,
        int successCount,
        int failureCount,
        List<String> failedRows
) {
}
