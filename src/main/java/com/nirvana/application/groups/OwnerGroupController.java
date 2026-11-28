package com.nirvana.application.groups;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** The desk and schedule show which appointments belong to one group. Front-desk staff may read it. */
@RestController
@RequestMapping("/api/owner/spas/{spaId}")
@RequiredArgsConstructor
@PreAuthorize("@staffAccess.desk(#spaId)")
public class OwnerGroupController {
    private final GroupBookings groups;
    private final GroupAvailability availability;

    public record SideBySide(Integer capacity) {}

    @GetMapping("/booking-groups")
    public List<GroupBookings.DeskGroup> groups(@PathVariable long spaId,
                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        if (!to.isAfter(from) || to.isAfter(from.plusDays(62))) throw new IllegalArgumentException("Choose a range of up to 62 days");
        return groups.forSpa(spaId, from, to);
    }

    @GetMapping("/side-by-side-services")
    public Map<Long, Integer> sideBySide(@PathVariable long spaId) {
        return availability.sideBySide(availability.serviceIds(spaId));
    }

    /** Owners and managers mark a service as a couples room / side-by-side treatment. */
    @PreAuthorize("@staffAccess.spa(#spaId)")
    @PutMapping("/services/{serviceId}/side-by-side")
    public SideBySide setSideBySide(@PathVariable long spaId, @PathVariable long serviceId, @RequestBody SideBySide request) {
        return new SideBySide(groups.setSideBySide(spaId, serviceId, request == null ? null : request.capacity()));
    }
}
