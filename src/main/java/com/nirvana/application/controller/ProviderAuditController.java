package com.nirvana.application.controller;

import com.nirvana.application.model.dto.ProviderAuditLogResponse;
import com.nirvana.application.service.ProviderAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class ProviderAuditController {

    private final ProviderAuditService providerAuditService;

    @GetMapping
    public List<ProviderAuditLogResponse> getAuditForSpa(@RequestParam Long spaId) {
        return providerAuditService.listForSpa(spaId).stream()
                .map(log -> {
                    ProviderAuditLogResponse response = new ProviderAuditLogResponse();
                    response.setId(log.getId());
                    response.setSpaId(log.getSpa() != null ? log.getSpa().getId() : null);
                    response.setActorType(log.getActorType());
                    response.setActorId(log.getActorId());
                    response.setAction(log.getAction());
                    response.setDetails(log.getDetails());
                    response.setCreatedAt(log.getCreatedAt());
                    return response;
                })
                .collect(Collectors.toList());
    }
}
