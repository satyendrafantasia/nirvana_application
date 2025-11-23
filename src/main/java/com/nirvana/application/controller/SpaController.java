// src/main/java/com/nirvana/application/controller/SpaController.java
package com.nirvana.application.controller;

import com.nirvana.application.model.dto.ServiceSummaryResponse;
import com.nirvana.application.model.dto.SpaDetailResponse;
import com.nirvana.application.model.dto.SpaRequestDTO;
import com.nirvana.application.model.dto.SpaResponseDTO;
import com.nirvana.application.service.impl.SpaReadService;
import com.nirvana.application.service.SpaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/spas")
@RequiredArgsConstructor
public class SpaController {

    private final SpaService spaService;       // your existing write/CRUD service
    private final SpaReadService spaReadService; // new read-oriented service

    @PostMapping
    public SpaResponseDTO createSpa(@Valid @RequestBody SpaRequestDTO request) {
        return spaService.createSpa(request);
    }

    // SPA DETAILS for detail screen
    @GetMapping("/{id}")
    public SpaDetailResponse getSpa(@PathVariable Long id) {
        return spaReadService.getSpaDetails(id);
    }

    // SPA SERVICES for service list on detail screen
    @GetMapping("/{id}/services")
    public List<ServiceSummaryResponse> getSpaServices(@PathVariable Long id) {
        return spaReadService.getSpaServices(id);
    }

    @PutMapping("/{id}")
    public SpaResponseDTO updateSpa(@PathVariable Long id,
                                    @Valid @RequestBody SpaRequestDTO request) {
        return spaService.updateSpa(id, request);
    }

    @DeleteMapping("/{id}")
    public void deactivateSpa(@PathVariable Long id) {
        spaService.deactivateSpa(id);
    }
}
