package com.nirvana.application.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.LeadOnboardingService;
import com.nirvana.application.service.SpaLeadService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Spas the team is approaching about joining Nirvana. Admins only. */
@RestController
@RequestMapping("/api/admin/spa-leads")
@PreAuthorize("@staffAccess.admin()")
@RequiredArgsConstructor
public class SpaLeadController {
    private final SpaLeadService leads;
    private final LeadOnboardingService onboarding;

    public record LeadUpdate(@Size(max = 20) String status, @Size(max = 2000) String notes) {}

    /** Body: a JSON array of spas as exported from Google Maps scrapers. */
    @PostMapping("/import")
    public SpaLeadService.ImportResult importLeads(@RequestBody JsonNode rows,
                                                  @RequestParam(required = false) @Size(max = 100) String source,
                                                  @RequestParam(required = false) @Size(max = 100) String city) {
        return leads.importLeads(rows, source, city);
    }

    /** Empty leadIds means every lead not yet onboarded. */
    public record OnboardRequest(@Size(max = 1000) java.util.List<Long> leadIds,
                                 @jakarta.validation.constraints.AssertTrue(message = "Confirm each spa agreed to be listed on Nirvana") boolean permissionConfirmed,
                                 @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(60) Integer days) {}

    /** Publishes consented imports with standard editable inventory. */
    @PostMapping("/onboard")
    public LeadOnboardingService.Result onboard(@Valid @RequestBody OnboardRequest body) {
        return onboarding.onboard(body.leadIds(), body.days() == null ? 30 : body.days(), SecurityUtils.getCurrentUserId());
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String status,
                                                    @RequestParam(required = false) @Size(max = 100) String q,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(leads.list(status, q, page, size));
    }

    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public record OwnerAssignment(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email String email) {}

    @PostMapping("/{id}/owner")
    @org.springframework.transaction.annotation.Transactional
    public void assignOwner(@PathVariable long id, @Valid @RequestBody OwnerAssignment in) {
        var accounts = jdbc.queryForList("""
                SELECT u.id FROM app_user u JOIN role r ON r.id=u.role_id
                WHERE u.email=? AND u.active=1 AND u.is_active=1 AND u.deleted_at IS NULL AND r.role_type='Spa_MANAGER'
                """, Long.class, in.email().strip());
        if (accounts.size()!=1) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Choose an active spa owner account.");
        jdbc.update("INSERT INTO spa_manager(user_id) VALUES (?) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)",accounts.get(0));
        int changed=jdbc.update("""
                UPDATE spa s JOIN spa_lead l ON l.spa_id=s.id JOIN spa_manager m ON m.user_id=?
                SET s.spa_manager_id=m.id,s.version=s.version+1
                WHERE l.id=? AND l.status='ONBOARDED' AND s.spa_manager_id IS NULL AND JSON_EXTRACT(s.meta,'$.standardInventory') IS NOT NULL
                """,accounts.get(0),id);
        if (changed!=1) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"This listing is already assigned or is not a standard imported spa.");
    }

    @GetMapping("/{id}/inventory")
    public Map<String,Object> inventory(@PathVariable long id) {
        Long spaId = jdbc.queryForObject("SELECT spa_id FROM spa_lead WHERE id=?", Long.class, id);
        if (spaId == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Prepare the draft first.");
        return Map.of("services", jdbc.queryForList("SELECT name, price_cents AS price, duration_min AS minutes FROM service WHERE spa_id=? AND is_active=1 ORDER BY name", spaId),
                "hours", jdbc.queryForList("SELECT weekday, CAST(open_local AS CHAR) AS opens, CAST(close_local AS CHAR) AS closes FROM schedule_rule WHERE spa_id=? AND is_holiday=0 ORDER BY weekday", spaId),
                "capacity", jdbc.queryForObject("SELECT max_concurrent_services FROM spa WHERE id=?", Integer.class, spaId));
    }

    @PatchMapping("/{id}")
    public Map<String, String> update(@PathVariable long id, @Valid @RequestBody LeadUpdate body) {
        leads.update(id, body.status(), body.notes());
        return Map.of("status", "saved");
    }
}
