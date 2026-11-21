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

    /** Makes leads live, searchable and bookable with the starter menu. */
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

    @PatchMapping("/{id}")
    public Map<String, String> update(@PathVariable long id, @Valid @RequestBody LeadUpdate body) {
        leads.update(id, body.status(), body.notes());
        return Map.of("status", "saved");
    }
}
