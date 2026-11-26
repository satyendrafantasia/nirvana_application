package com.nirvana.application.service.gst;

import com.nirvana.application.model.dto.InvoiceEmailDto;
import com.nirvana.application.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.HtmlUtils;

import java.time.ZoneOffset;
import java.util.Map;

/** Emails a guest their GST document through the existing invoice email, once the issue has committed. */
@Component
@RequiredArgsConstructor
@Slf4j
public class GstMail {
    private final EmailService email;
    private final JdbcTemplate db;

    @Value("${app.frontend-url}")
    String frontendUrl;

    /** Sends after the surrounding transaction commits, so a rolled-back document is never emailed. */
    public void afterCommit(GstDocument doc, GstDocument original) {
        if (GstDocument.COMMISSION_INVOICE.equals(doc.docType()) || doc.recipientEmail() == null || doc.recipientEmail().isBlank()) return;
        Runnable send = () -> {
            try {
                email.sendInvoiceEmail(dto(doc, original));
            } catch (RuntimeException e) {
                log.error("event=gst.document.email_failed number={}", doc.documentNumber(), e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        } else {
            send.run();
        }
    }

    InvoiceEmailDto dto(GstDocument doc, GstDocument original) {
        Map<String, Object> booking = doc.bookingId() == null ? Map.of() : db.queryForList("""
                SELECT b.booking_reference,b.start_ts,b.end_ts,b.guest_count,s.name AS service_name,p.timezone,p.name AS spa_name
                FROM booking b JOIN service s ON s.id=b.service_id JOIN spa p ON p.id=b.spa_id WHERE b.id=?""", doc.bookingId())
                .stream().findFirst().orElse(Map.of());
        return InvoiceEmailDto.builder()
                .documentTitle(doc.title())
                .invoiceNumber(doc.documentNumber())
                .issuedAt(doc.issuedAt().atOffset(ZoneOffset.UTC))
                .toEmail(doc.recipientEmail())
                .toName(doc.recipientName())
                .spaName(booking.get("spa_name") != null ? booking.get("spa_name").toString()
                        : doc.supplierTradeName() != null ? doc.supplierTradeName() : doc.supplierName())
                .spaAddressLine1(doc.supplierAddress())
                .spaTimezone(booking.get("timezone") == null ? "Asia/Kolkata" : booking.get("timezone").toString())
                .bookingReference(booking.get("booking_reference") != null ? booking.get("booking_reference").toString() : doc.notes())
                .bookingStartTs(utc(booking.get("start_ts")))
                .serviceName(doc.description())
                .guestCount(doc.quantity())
                .currency(doc.currency())
                .totalCents((int) (long) doc.totalCents())
                .supplierName(doc.supplierName())
                .supplierGstin(doc.supplierGstin())
                .originalDocumentNumber(original == null ? null : original.documentNumber())
                .taxableCents(doc.taxableCents())
                .cgstCents(doc.cgstCents())
                .sgstCents(doc.sgstCents())
                .igstCents(doc.igstCents())
                .rateLabel(GstMath.percent(doc.rateBps()))
                .invoicePdfUrl(frontendUrl.replaceAll("/+$", "") + (doc.bookingId() != null ? "/me/bookings" : "/me/packages"))
                .build();
    }

    /** A DATETIME column (stored in UTC) as the driver hands it back. */
    private static java.time.OffsetDateTime utc(Object v) {
        if (v instanceof java.time.LocalDateTime t) return t.atOffset(ZoneOffset.UTC);
        if (v instanceof java.sql.Timestamp t) return t.toLocalDateTime().atOffset(ZoneOffset.UTC);
        if (v instanceof java.time.OffsetDateTime t) return t;
        return null;
    }

    // ---- Shared lines for the invoice email bodies.

    public static void intro(StringBuilder sb, InvoiceEmailDto dto) {
        sb.append("<p>Your ").append(e(dto.documentTitle().toLowerCase())).append(" from ")
                .append(e(dto.supplierName())).append(" is ready.");
        if (dto.supplierGstin() != null) sb.append(" GSTIN ").append(e(dto.supplierGstin())).append('.');
        sb.append("</p>");
        if (dto.originalDocumentNumber() != null) {
            sb.append("<p>It credits invoice ").append(e(dto.originalDocumentNumber())).append(" for your refund.</p>");
        }
    }

    public static void amounts(StringBuilder sb, InvoiceEmailDto dto) {
        String c = dto.currency() == null ? "INR" : dto.currency();
        boolean taxed = n(dto.cgstCents()) + n(dto.sgstCents()) + n(dto.igstCents()) > 0;
        sb.append("<li><strong>").append(taxed ? "Taxable value" : "Value of supply").append("</strong>: ")
                .append(money(c, n(dto.taxableCents()))).append("</li>");
        if (n(dto.cgstCents()) > 0 || n(dto.sgstCents()) > 0) {
            sb.append("<li><strong>CGST</strong>: ").append(money(c, n(dto.cgstCents()))).append("</li>");
            sb.append("<li><strong>SGST</strong>: ").append(money(c, n(dto.sgstCents()))).append("</li>");
        }
        if (n(dto.igstCents()) > 0) sb.append("<li><strong>IGST</strong>: ").append(money(c, n(dto.igstCents()))).append("</li>");
        if (taxed && dto.rateLabel() != null) sb.append("<li><strong>GST rate</strong>: ").append(e(dto.rateLabel())).append("</li>");
        sb.append("<li><strong>Total</strong>: ").append(money(c, n(dto.totalCents()))).append("</li>");
    }

    private static String money(String currency, long cents) {
        var f = java.text.NumberFormat.getNumberInstance(new java.util.Locale("en", "IN"));
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return e(currency) + " " + f.format(cents / 100.0);
    }

    private static long n(Number v) {
        return v == null ? 0 : v.longValue();
    }

    private static String e(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }
}
