package com.nirvana.application.controller;

import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.recovery.CheckoutRecoveryService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/** Reminder links for checkouts left unpaid: resuming, opting out, and the sent/clicked/recovered numbers. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CheckoutRecoveryController {
    private final CheckoutRecoveryService recovery;

    public record Preference(@NotNull Boolean enabled) {}

    /** Opens a reminder link: records the click and returns what to prefill in the booking flow. */
    @GetMapping("/me/checkout-recovery/{token}")
    public CheckoutRecoveryService.Resume resume(@PathVariable String token) {
        return recovery.resume(token, SecurityUtils.getCurrentUserId(), now());
    }

    /**
     * The footer link's page and mail apps' one-click unsubscribe (RFC 8058) both post here. The
     * signed token is the only credential; it names a reminder, not a person.
     */
    @PostMapping("/public/checkout-reminders/unsubscribe")
    public Map<String, Object> unsubscribe(@RequestParam("t") String token) {
        recovery.unsubscribe(token, now());
        return Map.of("remindersEnabled", false);
    }

    @PostMapping("/public/checkout-reminders/resubscribe")
    public Map<String, Object> resubscribe(@RequestParam("t") String token) {
        recovery.resubscribe(token, now());
        return Map.of("remindersEnabled", true);
    }

    @GetMapping("/me/checkout-reminders")
    public Map<String, Object> preference() {
        return Map.of("enabled", !recovery.optedOut(SecurityUtils.getCurrentUserId()));
    }

    @PutMapping("/me/checkout-reminders")
    public Map<String, Object> preference(@RequestBody @jakarta.validation.Valid Preference input) {
        recovery.setOptedOut(SecurityUtils.getCurrentUserId(), !input.enabled());
        return Map.of("enabled", input.enabled());
    }

    @GetMapping("/admin/checkout-recovery/stats")
    @PreAuthorize("@staffAccess.admin()")
    public CheckoutRecoveryService.Stats adminStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long spaId) {
        return stats(from, to, spaId);
    }

    @GetMapping("/owner/spas/{spa}/checkout-recovery/stats")
    @PreAuthorize("@staffAccess.spa(#spa)")
    public CheckoutRecoveryService.Stats ownerStats(
            @PathVariable long spa,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return stats(from, to, spa);
    }

    /** Defaults to the last 30 days; {@code to} is inclusive. */
    private CheckoutRecoveryService.Stats stats(LocalDate from, LocalDate to, Long spaId) {
        LocalDate last = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate first = from != null ? from : last.minusDays(29);
        if (first.isAfter(last)) throw new IllegalArgumentException("'from' must not be after 'to'");
        if (first.isBefore(last.minusDays(366))) throw new IllegalArgumentException("Choose a range of at most a year");
        return recovery.stats(first.atStartOfDay().atOffset(ZoneOffset.UTC),
                last.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC), spaId);
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
