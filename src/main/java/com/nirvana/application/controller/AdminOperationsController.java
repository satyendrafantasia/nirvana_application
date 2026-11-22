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
@org.springframework.security.access.prepost.PreAuthorize("@staffAccess.admin()")
public class AdminOperationsController {

    private final OperationsObservabilityService operationsObservabilityService;

    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @GetMapping("/booking-alerts")
    public java.util.List<java.util.Map<String,Object>> bookingAlerts() {
        return jdbc.queryForList("""
                SELECT j.id,b.booking_reference,s.name AS spa,s.phone,j.event_kind,j.audience,j.state,j.attempts,j.next_attempt
                FROM booking_alert_job j JOIN booking b ON b.id=j.booking_id JOIN spa s ON s.id=b.spa_id
                WHERE j.state IN ('PENDING','FAILED','NEEDS_CONTACT') ORDER BY (j.state='PENDING'),j.next_attempt,j.id LIMIT 500
                """);
    }

    @GetMapping("/dashboard")
    public OperationsDashboardResponse dashboard() {
        return operationsObservabilityService.snapshot();
    }
}
