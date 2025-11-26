package com.nirvana.application.service;

import com.nirvana.application.model.dto.SpaPackageUsageCheckResponse;

public interface SpaPackageUsageService {

    SpaPackageUsageCheckResponse canUseSpaPackage(Long userId, Long spaId);

    void consumeSession(Long userId, Long spaId, Long bookingId, String notes);
}
