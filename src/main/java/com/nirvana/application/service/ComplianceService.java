package com.nirvana.application.service;

import com.nirvana.application.model.dto.ConsentUpdateRequest;
import com.nirvana.application.model.User;

public interface ComplianceService {

    User recordConsent(Long userId, ConsentUpdateRequest request);
}
