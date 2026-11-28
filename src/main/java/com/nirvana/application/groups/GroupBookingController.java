package com.nirvana.application.groups;

import com.nirvana.application.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Customer side of couples and group bookings. Single-guest changes use the ordinary booking endpoints. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GroupBookingController {
    private final GroupAvailability availability;
    private final GroupBookings groups;

    public record AvailabilityRequest(LocalDate date, List<GroupAvailability.GuestRequest> guests) {}

    public record CancelRequest(Integer expectedRefundCents) {}

    /** Times on {@code date} at which every guest can start together. */
    @PostMapping("/spas/{spaId}/group-availability")
    public List<GroupAvailability.GroupTime> times(@PathVariable long spaId, @RequestBody AvailabilityRequest request) {
        if (request == null || request.date() == null) throw new IllegalArgumentException("Choose a date");
        return availability.times(spaId, request.date(), request.guests());
    }

    /** Which of the spa's services are offered side by side in a couples room, and for how many. */
    @GetMapping("/spas/{spaId}/side-by-side-services")
    public Map<Long, Integer> sideBySide(@PathVariable long spaId) {
        var ids = availability.serviceIds(spaId);
        return availability.sideBySide(ids);
    }

    @PostMapping("/booking-groups/holds")
    public GroupBookings.HoldReply hold(@RequestBody GroupBookings.HoldRequest request) {
        return groups.hold(SecurityUtils.getCurrentUserId(), request);
    }

    @PostMapping("/booking-groups")
    public GroupBookings.GroupView create(@RequestBody GroupBookings.CreateRequest request) {
        return groups.create(SecurityUtils.getCurrentUserId(), request);
    }

    @GetMapping("/booking-groups/{groupId}")
    public GroupBookings.GroupView view(@PathVariable long groupId) {
        return groups.view(groupId, SecurityUtils.getCurrentUserId());
    }

    @GetMapping("/me/booking-groups")
    public List<GroupBookings.GroupView> mine() {
        return groups.mine(SecurityUtils.getCurrentUserId());
    }

    @GetMapping("/booking-groups/{groupId}/cancellation-quote")
    public GroupBookings.GroupQuote quote(@PathVariable long groupId) {
        return groups.quote(groupId, SecurityUtils.getCurrentUserId());
    }

    @PostMapping("/booking-groups/{groupId}/cancel")
    public GroupBookings.GroupQuote cancel(@PathVariable long groupId, @RequestBody(required = false) CancelRequest request) {
        return groups.cancel(groupId, SecurityUtils.getCurrentUserId(), request == null ? null : request.expectedRefundCents());
    }
}
