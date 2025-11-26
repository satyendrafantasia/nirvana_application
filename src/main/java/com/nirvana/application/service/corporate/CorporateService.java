package com.nirvana.application.service.corporate;

import com.nirvana.application.model.dto.corporate.CorporateResponse;
import com.nirvana.application.model.dto.corporate.CreateCorporateRequest;

import java.util.List;

public interface CorporateService {
    CorporateResponse createCorporate(CreateCorporateRequest request);
    CorporateResponse getCorporate(Long id);
    List<CorporateResponse> listCorporates();
}
