package com.nirvana.application.rewards;

import com.nirvana.application.model.enums.AdminActionType;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.AuditTrailService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Gift cards for spa owners (sales and redemptions at their spa) and for Nirvana staff (issue and void). */
@RestController
@RequiredArgsConstructor
public class GiftCardAdminController {

    private final GiftCardService giftCards;
    private final AuditTrailService audit;
    private final JdbcTemplate db;

    /** Dates are days in the spa's time zone, both included; the last 30 days when left out. */
    @GetMapping("/api/owner/spas/{spa}/gift-cards")
    @PreAuthorize("@staffAccess.spa(#spa)")
    public Map<String, Object> ownerSummary(@PathVariable long spa, @RequestParam(required = false) LocalDate from,
                                            @RequestParam(required = false) LocalDate to) {
        ZoneId zone = ZoneId.of(Objects.toString(db.queryForObject("SELECT timezone FROM spa WHERE id=?", String.class, spa), "Asia/Kolkata"));
        LocalDate last = to == null ? LocalDate.now(zone) : to;
        LocalDate first = from == null ? last.minusDays(29) : from;
        if (first.isAfter(last)) throw new IllegalArgumentException("Choose a start date on or before the end date");
        Instant start = first.atStartOfDay(zone).toInstant(), end = last.plusDays(1).atStartOfDay(zone).toInstant();
        var summary = new java.util.LinkedHashMap<String, Object>(giftCards.ownerSummary(spa, start, end));
        summary.put("from", first);
        summary.put("to", last);
        return summary;
    }

    @GetMapping("/api/admin/gift-cards")
    @PreAuthorize("@staffAccess.admin()")
    public List<GiftCardService.Card> list(@RequestParam(required = false) String q) {
        return giftCards.adminList(q, 100);
    }

    @GetMapping("/api/admin/gift-cards/{id}/transactions")
    @PreAuthorize("@staffAccess.admin()")
    public List<Map<String, Object>> transactions(@PathVariable long id) {
        return giftCards.transactions(id);
    }

    public record Issue(@NotNull @Positive Integer amountCents, Long spaId, @Size(max = 100) String recipientName,
                        @NotBlank @Email @Size(max = 255) String recipientEmail, @Size(max = 300) String message,
                        Integer validityDays, @NotBlank @Size(max = 255) String note) {
    }

    @PostMapping("/api/admin/gift-cards")
    @PreAuthorize("@staffAccess.admin()")
    @Transactional
    public GiftCardService.Card issue(@Valid @RequestBody Issue input) {
        long admin = SecurityUtils.getCurrentUserId();
        var card = giftCards.issue(admin, new GiftCardService.IssueInput(input.amountCents(), input.spaId(), input.recipientName(),
                input.recipientEmail(), input.message(), input.validityDays(), input.note()));
        audit.record(AdminActionType.CREATE, "GIFT_CARD", card.id(), admin, null,
                Map.of("amountCents", card.initialCents(), "spaId", Objects.toString(card.spaId(), "any")), input.note());
        return card;
    }

    public record VoidInput(@NotBlank @Size(max = 300) String reason) {
    }

    @PostMapping("/api/admin/gift-cards/{id}/void")
    @PreAuthorize("@staffAccess.admin()")
    @Transactional
    public GiftCardService.Card voidCard(@PathVariable long id, @Valid @RequestBody VoidInput input) {
        long admin = SecurityUtils.getCurrentUserId();
        int before = giftCards.card(id).balanceCents();
        var card = giftCards.voidCard(admin, id, input.reason());
        audit.record(AdminActionType.STATUS_CHANGE, "GIFT_CARD", id, admin, Map.of("balanceCents", before),
                Map.of("status", card.status()), input.reason());
        return card;
    }
}
