package com.nirvana.application.service.corporate;

import com.nirvana.application.model.dto.corporate.CorporateEmployeeUploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface CorporateOnboardingService {
    CorporateEmployeeUploadResponse uploadEmployeeFile(Long corporateId, Long corporateDealId, MultipartFile file);
}
