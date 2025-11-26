package com.nirvana.application.repository.corporate;

import com.nirvana.application.model.corporate.CorporateOnboardingUpload;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CorporateOnboardingUploadRepository extends JpaRepository<CorporateOnboardingUpload, Long> {
    List<CorporateOnboardingUpload> findByCorporateIdOrderByCreatedAtDesc(Long corporateId);
}
