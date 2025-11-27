package com.nirvana.application.controller;

import com.nirvana.application.model.dto.OperationsDashboardResponse;
import com.nirvana.application.service.OperationsObservabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/operations")
@RequiredArgsConstructor
public class AdminOperationsController {

    private final OperationsObservabilityService operationsObservabilityService;

    @GetMapping("/dashboard")
    public OperationsDashboardResponse dashboard() {
        return operationsObservabilityService.snapshot();
    }
}
