package com.nirvana.application.repository.corporate;

import com.nirvana.application.model.corporate.CorporateDeal;
import com.nirvana.application.model.enums.CorporateDealStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CorporateDealRepository extends JpaRepository<CorporateDeal, Long> {
    List<CorporateDeal> findByCorporateId(Long corporateId);
    List<CorporateDeal> findByCorporateIdAndStatus(Long corporateId, CorporateDealStatus status);
}
