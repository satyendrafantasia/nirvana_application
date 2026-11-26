package com.nirvana.application.controller;

import com.nirvana.application.service.impl.BookingLifecycleService;
import com.nirvana.application.service.impl.BookingLifecycleService.StatusChange;
import com.nirvana.application.model.dto.BookingCancelResponse;
import com.nirvana.application.security.StaffAccess;
import com.nirvana.application.service.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.RequestBody;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Front-desk actions a spa takes on its own appointments. */
@RestController
@RequestMapping("/api/owner/spas/{spaId}/bookings/{bookingId}")
@PreAuthorize("@staffAccess.desk(#spaId)")
@RequiredArgsConstructor
public class OwnerBookingController {

    private final BookingLifecycleService lifecycle;
    private final BookingService bookingService;
    private final StaffAccess staffAccess;
    private final com.nirvana.application.service.AuditTrailService audit;
    private void record(Long id,String action,String reason){
        audit.record(com.nirvana.application.model.enums.AdminActionType.STATUS_CHANGE,"BOOKING",id,
            com.nirvana.application.security.SecurityUtils.getCurrentUserId(),null,java.util.Map.of("action",action),reason);
    }

    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/arrive")
    public StatusChange arrive(@PathVariable Long spaId, @PathVariable Long bookingId) {
        var change=lifecycle.markArrived(spaId, bookingId);
        record(bookingId,"arrive","arrive");
        return change;
    }

    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/complete")
    public StatusChange complete(@PathVariable Long spaId, @PathVariable Long bookingId) {
        var change=lifecycle.markCompleted(spaId, bookingId);
        record(bookingId,"complete","complete");
        return change;
    }

    public record CancelRequest(@NotBlank @Size(max = 500) String reason) {
    }

    /** The spa backs out: the customer gets a full refund and any package session back. */
    @PostMapping("/cancel")
    public BookingCancelResponse cancel(@PathVariable Long spaId, @PathVariable Long bookingId,
                                        @Valid @RequestBody CancelRequest request) {
        return bookingService.cancelBookingBySpa(spaId, bookingId, request.reason().trim(), staffAccess.admin());
    }

    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/no-show")
    public StatusChange noShow(@PathVariable Long spaId, @PathVariable Long bookingId) {
        var change=lifecycle.markNoShow(spaId, bookingId);
        record(bookingId,"no-show","no-show");
        return change;
    }
}
