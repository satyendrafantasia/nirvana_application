package com.nirvana.application.controller;

import com.nirvana.application.model.enums.AdminActionType;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.AuditTrailService;
import com.nirvana.application.service.payout.PayoutAccountService;
import com.nirvana.application.service.payout.PayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** The spa owner's payouts: bank details, what's waiting to be paid, and a statement for each payout. */
@RestController
@RequestMapping("/api/owner/spas/{spa}/payouts")
@PreAuthorize("@staffAccess.spa(#spa)")
@RequiredArgsConstructor
public class OwnerPayoutController {
    private final PayoutService payouts;
    private final PayoutAccountService accounts;
    private final AuditTrailService audit;

    @GetMapping
    public Map<String, Object> overview(@PathVariable long spa, @RequestParam(defaultValue = "0") int page) {
        var items = payouts.list(spa, null, 26, Math.max(0, page) * 25);
        var result = new LinkedHashMap<String, Object>();
        result.put("automatic", payouts.automatic());
        result.put("account", accounts.view(spa));
        result.put("upcoming", payouts.upcoming(spa));
        result.put("items", items.subList(0, Math.min(25, items.size())));
        result.put("hasMore", items.size() > 25);
        return result;
    }

    @GetMapping("/{id}")
    public Map<String, Object> statement(@PathVariable long spa, @PathVariable long id) {
        var statement = payouts.statement(id);
        if (((Number) statement.get("spaId")).longValue() != spa) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such payout");
        return statement;
    }

    @PutMapping("/account")
    public Map<String, Object> saveAccount(@PathVariable long spa, @RequestBody PayoutAccountService.BankDetails details) {
        var saved = accounts.save(spa, details, SecurityUtils.getCurrentUserId());
        audit.record(AdminActionType.UPDATE, "SPA_PAYOUT_ACCOUNT", spa, SecurityUtils.getCurrentUserId(), null,
                Map.of("ifsc", saved.get("ifsc"), "account", saved.get("accountMasked")), "Payout bank details saved");
        return saved;
    }
}
