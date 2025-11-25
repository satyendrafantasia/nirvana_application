package com.nirvana.application.controller;

import com.nirvana.application.model.dto.SupportOverrideRequest;
import com.nirvana.application.model.dto.SupportOverrideResolutionRequest;
import com.nirvana.application.model.dto.SupportOverrideResponse;
import com.nirvana.application.service.SupportOverrideService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/support-overrides")
@RequiredArgsConstructor
public class SupportOverrideController {

    private final SupportOverrideService supportOverrideService;

    @PostMapping
    public SupportOverrideResponse requestOverride(@Valid @RequestBody SupportOverrideRequest request) {
        return supportOverrideService.requestOverride(request);
    }

    @PutMapping("/{id}")
    public SupportOverrideResponse resolve(@PathVariable Long id,
                                           @Valid @RequestBody SupportOverrideResolutionRequest request) {
        return supportOverrideService.resolve(id, request);
    }

    @GetMapping
    public List<SupportOverrideResponse> list(@RequestParam Long spaId) {
        return supportOverrideService.listForSpa(spaId);
    }
}
