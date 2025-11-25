package com.nirvana.application.controller;

import com.nirvana.application.model.dto.ProviderAnalyticsResponse;
import com.nirvana.application.service.ProviderAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
public class ProviderAnalyticsController {

    private final ProviderAnalyticsService providerAnalyticsService;

    @GetMapping("/spa/{spaId}")
    public ProviderAnalyticsResponse getSpaAnalytics(@PathVariable Long spaId) {
        return providerAnalyticsService.getSpaSummary(spaId);
    }
}
