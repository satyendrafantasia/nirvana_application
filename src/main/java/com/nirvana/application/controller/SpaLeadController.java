package com.nirvana.application.controller;

import com.fasterxml.jackson.databind.JsonNode;
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

    public record LeadUpdate(@Size(max = 20) String status, @Size(max = 2000) String notes) {}

    /** Body: a JSON array of spas as exported from Google Maps scrapers. */
    @PostMapping("/import")
    public SpaLeadService.ImportResult importLeads(@RequestBody JsonNode rows,
                                                  @RequestParam(required = false) @Size(max = 100) String source,
                                                  @RequestParam(required = false) @Size(max = 100) String city) {
        return leads.importLeads(rows, source, city);
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
