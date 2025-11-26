package com.nirvana.application.service.impl.corporate;

import com.nirvana.application.exception.CorporateNotFoundException;
import com.nirvana.application.model.corporate.Corporate;
import com.nirvana.application.model.dto.corporate.CorporateResponse;
import com.nirvana.application.model.dto.corporate.CreateCorporateRequest;
import com.nirvana.application.repository.corporate.CorporateRepository;
import com.nirvana.application.service.corporate.CorporateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CorporateServiceImpl implements CorporateService {

    private final CorporateRepository corporateRepository;

    @Override
    public CorporateResponse createCorporate(CreateCorporateRequest request) {
        Corporate corporate = Corporate.builder()
                .name(request.name())
                .domain(request.domain())
                .contactPerson(request.contactPerson())
                .contactEmail(request.contactEmail())
                .build();
        corporateRepository.save(corporate);
        return toResponse(corporate);
    }

    @Override
    @Transactional(readOnly = true)
    public CorporateResponse getCorporate(Long id) {
        Corporate corporate = corporateRepository.findById(id)
                .orElseThrow(() -> new CorporateNotFoundException(id));
        return toResponse(corporate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CorporateResponse> listCorporates() {
        return corporateRepository.findAll().stream().map(this::toResponse).toList();
    }

    private CorporateResponse toResponse(Corporate corporate) {
        return new CorporateResponse(
                corporate.getId(),
                corporate.getName(),
                corporate.getDomain(),
                corporate.getContactPerson(),
                corporate.getContactEmail(),
                corporate.getStatus()
        );
    }
}
