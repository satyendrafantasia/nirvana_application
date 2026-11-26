package com.nirvana.application.controller;

import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.security.StaffAccess;
import com.nirvana.application.service.gst.GstDocument;
import com.nirvana.application.service.gst.GstDocumentHtml;
import com.nirvana.application.service.gst.GstInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * GST documents: the guest's invoices and credit notes (issued by the spa), and Nirvana's commission
 * invoices to the spa. Each document downloads as a printable HTML page.
 */
@RestController
@RequiredArgsConstructor
public class GstDocumentController {
    private final GstInvoiceService gst;
    private final StaffAccess access;
    private final JdbcTemplate db;
    private final com.nirvana.application.service.impl.PaymentService payments;

    /** What a list shows of a document. */
    public record DocumentView(Long id, String docType, String title, String documentNumber, LocalDate issueDate,
                               Long bookingId, Long subscriptionId, Long originalDocumentId, String supplierName,
                               String supplierGstin, String recipientName, String description, String currency,
                               long taxableCents, long cgstCents, long sgstCents, long igstCents, long totalCents,
                               LocalDate periodStart, LocalDate periodEnd, String htmlUrl) {
        static DocumentView of(GstDocument d) {
            return new DocumentView(d.id(), d.docType(), d.title(), d.documentNumber(), d.issueDate(), d.bookingId(),
                    d.subscriptionId(), d.originalDocumentId(), d.supplierName(), d.supplierGstin(), d.recipientName(),
                    d.description(), d.currency(), n(d.taxableCents()), n(d.cgstCents()), n(d.sgstCents()), n(d.igstCents()),
                    n(d.totalCents()), d.periodStart(), d.periodEnd(), "/api/gst-documents/" + d.id() + "/html");
        }

        private static long n(Long v) {
            return v == null ? 0 : v;
        }
    }

    /** The booking's invoice and any credit notes, issuing whichever are due. */
    @GetMapping("/api/bookings/{bookingId}/gst-documents")
    public List<DocumentView> forBooking(@PathVariable Long bookingId) {
        payments.requireBookingAccess(bookingId);
        gst.creditBookingRefunds(bookingId);
        return gst.forBooking(bookingId).stream().map(DocumentView::of).toList();
    }

    /** A package purchase's invoice and any credit notes, for the guest who bought it. */
    @GetMapping("/api/me/spa-packages/{subscriptionId}/gst-documents")
    public List<DocumentView> forPackage(@PathVariable Long subscriptionId) {
        var owner = db.queryForList("SELECT user_id,spa_id FROM user_spa_package_subscription WHERE id=?", subscriptionId);
        if (owner.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Package purchase not found");
        long user = ((Number) owner.get(0).get("user_id")).longValue();
        long spa = ((Number) owner.get(0).get("spa_id")).longValue();
        if (!SecurityUtils.getCurrentUserId().equals(user) && !access.desk(spa)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Package purchase not found");
        }
        gst.creditPackageRefunds(subscriptionId);
        return gst.forSubscription(subscriptionId).stream().map(DocumentView::of).toList();
    }

    /** The document as a printable page. */
    @GetMapping(value = "/api/gst-documents/{id}/html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> html(@PathVariable Long id) {
        GstDocument d = gst.find(id).filter(this::mayRead)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        GstDocument original = d.originalDocumentId() == null ? null : gst.find(d.originalDocumentId()).orElse(null);
        String file = d.documentNumber().replace('/', '-') + ".html";
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Content-Disposition", "inline; filename=" + file)
                .header("X-Content-Type-Options", "nosniff")
                .contentType(new MediaType(MediaType.TEXT_HTML, java.nio.charset.StandardCharsets.UTF_8))
                .body(GstDocumentHtml.render(d, original));
    }

    /** The guest, the spa's desk, or an admin; Nirvana's commission invoices only to the owner and admins. */
    private boolean mayRead(GstDocument d) {
        if (GstDocument.COMMISSION_INVOICE.equals(d.docType())) return access.spa(d.spaId());
        return SecurityUtils.getCurrentUserId().equals(d.userId()) || access.desk(d.spaId());
    }

    /** The spa's documents issued in a date range (India time, inclusive). Defaults to this month. */
    @GetMapping("/api/owner/spas/{spa}/gst-documents")
    @PreAuthorize("@staffAccess.spa(#spa)")
    public List<DocumentView> forSpa(@PathVariable Long spa, @RequestParam(required = false) LocalDate from,
                                     @RequestParam(required = false) LocalDate to) {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        LocalDate start = from == null ? today.withDayOfMonth(1) : from, end = to == null ? today : to;
        if (end.isBefore(start) || start.plusDays(400).isBefore(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a range of up to a year.");
        }
        return gst.forSpa(spa, start, end).stream().map(DocumentView::of).toList();
    }

    /** Nirvana's commission invoice to the spa for a month that has ended, issued on first request. */
    @PostMapping("/api/owner/spas/{spa}/commission-invoices/{month}")
    @PreAuthorize("@staffAccess.spa(#spa)")
    public ResponseEntity<DocumentView> commission(@PathVariable Long spa, @PathVariable String month) {
        YearMonth period;
        try {
            period = YearMonth.parse(month);
        } catch (java.time.format.DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Month must look like 2026-10.");
        }
        return gst.issueCommission(spa, period).map(DocumentView::of).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
