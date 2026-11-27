package com.nirvana.application.controller;

import com.nirvana.application.model.dto.OperationsDashboardResponse;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/admin/operations")
@RequiredArgsConstructor
@PreAuthorize("@staffAccess.admin()")
public class AdminOperationsController {
    private final OperationsObservabilityService operationsObservabilityService;
    private final JdbcTemplate jdbc;
    private final OwnerPush push;
    private final SpaAlertSms sms;
    private final SpaAlertWhatsApp whatsapp;

    @Value("${spa-alerts.enabled:true}") private boolean spaAlertsEnabled;

    @GetMapping("/booking-alerts/readiness")
    public Map<String,Object> readiness() {
        return Map.of("smsEnabled",spaAlertsEnabled && sms.enabled(),"whatsappEnabled",spaAlertsEnabled && whatsapp.enabled(),
                "pushConfigured",push.ready(),"pushCredentialsAvailable",push.credentialsAvailable(),"pushDevices",jdbc.queryForObject("SELECT COUNT(*) FROM owner_push_device",Integer.class));
    }

    @GetMapping("/booking-alerts")
    public List<Map<String,Object>> bookingAlerts(@RequestParam(defaultValue="0") long after,
                                                 @RequestParam(defaultValue="false") boolean resolved) {
        return jdbc.queryForList("""
            SELECT j.id,b.booking_reference AS bookingReference,j.request_id AS requestId,s.name AS spa,
              CASE WHEN j.audience LIKE 'SPA_%' THEN s.phone ELSE COALESCE(NULLIF(u.phone,''),g.contact) END AS phone,
              CASE WHEN j.audience LIKE 'SPA_%' THEN COALESCE(NULLIF(s.email,''),m.email) ELSE u.email END AS email,
              j.event_kind AS event,j.audience,j.state,j.attempts,j.assigned_to AS assignedTo,
              COALESCE(a.name,a.email,CAST(j.assigned_to AS CHAR)) AS assignee,
              CASE WHEN c.status='CONFIRMED' AND b.notification_revision=c.delivery_revision THEN 1 ELSE 0 END AS acknowledged,
              (SELECT h.note FROM booking_alert_action h WHERE h.job_id=j.id AND h.action_name='resolve' ORDER BY h.id DESC LIMIT 1) AS outcome
            FROM booking_alert_job j LEFT JOIN booking b ON b.id=j.booking_id
            LEFT JOIN appointment_request r ON r.id=j.request_id
            JOIN spa s ON s.id=COALESCE(b.spa_id,r.spa_id)
            LEFT JOIN spa_manager sm ON sm.id=s.spa_manager_id LEFT JOIN app_user m ON m.id=sm.user_id
            LEFT JOIN app_user u ON u.id=COALESCE(b.user_id,r.user_id)
            LEFT JOIN desk_guest g ON g.spa_id=s.id AND g.user_id=u.id
            LEFT JOIN app_user a ON a.id=j.assigned_to LEFT JOIN spa_booking_confirmation c ON c.booking_id=b.id
            WHERE j.id>? AND ( (?=1 AND j.state='RESOLVED') OR (?=0 AND j.state IN ('FAILED','NEEDS_CONTACT')) )
            ORDER BY j.id LIMIT 51
            """, after,resolved,resolved);
    }

    public record Action(@Size(max=500) String note) {}

    @PostMapping("/booking-alerts/{id}/{action:assign|retry|resolve}")
    @Transactional
    public void act(@PathVariable long id,@PathVariable String action,@Valid @RequestBody(required=false) Action input) {
        long actor=SecurityUtils.getCurrentUserId();
        var jobs=jdbc.queryForList("SELECT state,assigned_to,event_kind FROM booking_alert_job WHERE id=? FOR UPDATE",id);
        if (jobs.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Alert not found");
        var job=jobs.get(0);
        if (!List.of("FAILED","NEEDS_CONTACT").contains(job.get("state"))) throw conflict("Alert changed. Refresh the queue.");
        Long assignee=job.get("assigned_to") instanceof Number n ? n.longValue() : null;
        if (assignee!=null && assignee!=actor) throw conflict("Another staff member is handling this alert");
        if (!"assign".equals(action) && !Objects.equals(assignee,actor)) throw conflict("Assign this alert to yourself first");
        String note=input==null || input.note()==null ? "" : input.note().strip();
        switch(action) {
            case "assign" -> jdbc.update("UPDATE booking_alert_job SET assigned_to=? WHERE id=?",actor,id);
            case "retry" -> {
                if ("FOLLOW_UP".equals(job.get("event_kind"))) throw conflict("Contact the spa and record the outcome");
                jdbc.update("UPDATE booking_alert_job SET state='PENDING',attempts=0,next_attempt=CURRENT_TIMESTAMP WHERE id=?",id);
            }
            case "resolve" -> {
                if (note.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Describe the contact outcome");
                jdbc.update("UPDATE booking_alert_job SET state='RESOLVED' WHERE id=?",id);
            }
            default -> throw new IllegalArgumentException("Unknown alert action");
        }
        jdbc.update("INSERT INTO booking_alert_action(job_id,actor_id,action_name,note) VALUES(?,?,?,?)",id,actor,action,note);
    }

    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }

    @GetMapping("/dashboard")
    public OperationsDashboardResponse dashboard() { return operationsObservabilityService.snapshot(); }
}
