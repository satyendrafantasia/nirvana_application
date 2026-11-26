package com.nirvana.application.controller;

import com.nirvana.application.model.enums.AdminActionType;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.AuditTrailService;
import com.nirvana.application.service.payout.PayoutAccountService;
import com.nirvana.application.service.payout.PayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/** Platform admins: run settlement, and see, retry or close payouts for every spa. */
@RestController
@RequestMapping("/api/admin/payouts")
@PreAuthorize("@staffAccess.admin()")
@RequiredArgsConstructor
public class AdminPayoutController {
    private final PayoutService payouts;
    private final PayoutAccountService accounts;
    private final AuditTrailService audit;

    public record Reference(String reference) {}
    public record LinkedAccount(String accountId) {}

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String status, @RequestParam(required = false) Long spa,
                                    @RequestParam(defaultValue = "0") int page) {
        var items = payouts.list(spa, status == null || status.isBlank() ? null : status, 51, Math.max(0, page) * 50);
        var result = new LinkedHashMap<String, Object>();
        result.put("mode", payouts.mode());
        result.put("automatic", payouts.automatic());
        result.put("items", items.subList(0, Math.min(50, items.size())));
        result.put("hasMore", items.size() > 50);
        return result;
    }

    /** Settles everything up to the end of {@code until} (default: today less the hold), then sends what's due. */
    @PostMapping("/run")
    public PayoutService.RunResult run(@RequestParam(required = false) LocalDate until) {
        var result = until == null ? payouts.run() : payouts.run(payouts.midnight(until.plusDays(1)));
        audit.record(AdminActionType.OTHER, "PAYOUT_RUN", 0L, SecurityUtils.getCurrentUserId(), null, result, "Settlement run started by admin");
        return result;
    }

    @GetMapping("/{id}")
    public Map<String, Object> statement(@PathVariable long id) {
        var statement = new LinkedHashMap<>(payouts.statement(id));
        statement.put("account", accounts.view(((Number) statement.get("spaId")).longValue()));
        return statement;
    }

    @PostMapping("/{id}/retry")
    public Map<String, Object> retry(@PathVariable long id) {
        var before = payouts.payout(id).get("status");
        var after = payouts.retry(id);
        audit.record(AdminActionType.STATUS_CHANGE, "PAYOUT", id, SecurityUtils.getCurrentUserId(), Map.of("status", before),
                Map.of("status", after.get("status")), "Payout retried");
        return after;
    }

    @PostMapping("/{id}/mark-paid")
    public Map<String, Object> markPaid(@PathVariable long id, @RequestBody Reference body) {
        var before = payouts.payout(id).get("status");
        var after = payouts.markPaid(id, body.reference());
        audit.record(AdminActionType.STATUS_CHANGE, "PAYOUT", id, SecurityUtils.getCurrentUserId(), Map.of("status", before),
                Map.of("status", "COMPLETED", "reference", after.get("transferId")), "Payout recorded as paid outside the gateway");
        return after;
    }

    @PutMapping("/spas/{spa}/linked-account")
    public Map<String, Object> linkedAccount(@PathVariable long spa, @RequestBody LinkedAccount body) {
        var saved = accounts.setLinkedAccount(spa, body.accountId());
        audit.record(AdminActionType.UPDATE, "SPA_PAYOUT_ACCOUNT", spa, SecurityUtils.getCurrentUserId(), null,
                Map.of("routeAccountId", saved.get("routeAccountId")), "Razorpay linked account set by admin");
        return saved;
    }
}
