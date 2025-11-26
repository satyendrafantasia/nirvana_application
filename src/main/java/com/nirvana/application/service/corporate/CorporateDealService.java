package com.nirvana.application.service.corporate;

import com.nirvana.application.model.dto.corporate.CorporateDealResponse;
import com.nirvana.application.model.dto.corporate.CreateCorporateDealRequest;

import java.util.List;

public interface CorporateDealService {
    CorporateDealResponse createCorporateDeal(Long corporateId, CreateCorporateDealRequest request);
    CorporateDealResponse updateCorporateDeal(Long corporateId, Long dealId, CreateCorporateDealRequest request);
    List<CorporateDealResponse> listCorporateDeals(Long corporateId);
    CorporateDealResponse activateDeal(Long corporateId, Long dealId);
    CorporateDealResponse deactivateDeal(Long corporateId, Long dealId);
}
