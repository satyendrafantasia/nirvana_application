package com.nirvana.application.service;

import com.nirvana.application.model.ProviderAuditLog;
import com.nirvana.application.repository.ProviderAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProviderAuditService {

    private final ProviderAuditLogRepository providerAuditLogRepository;

    public ProviderAuditLog log(Long spaId, String actorType, Long actorId, String action, String details) {
        ProviderAuditLog log = new ProviderAuditLog();
        if (spaId != null) {
            log.setSpa(new com.nirvana.application.model.Spa());
            log.getSpa().setId(spaId);
        }
        log.setActorType(actorType);
        log.setActorId(actorId);
        log.setAction(action);
        log.setDetails(details);
        return providerAuditLogRepository.save(log);
    }

    public List<ProviderAuditLog> listForSpa(Long spaId) {
        return providerAuditLogRepository.findBySpa_IdOrderByCreatedAtDesc(spaId);
    }
}
