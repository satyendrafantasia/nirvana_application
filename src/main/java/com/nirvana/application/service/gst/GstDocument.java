package com.nirvana.application.service.gst;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One row of gst_document: an issued invoice, bill of supply, credit note or commission invoice. */
public record GstDocument(
        Long id,
        String docType,
        String issuer,
        String documentNumber,
        String financialYear,
        String series,
        Long sequenceNumber,
        LocalDate issueDate,
        /** UTC. */
        LocalDateTime issuedAt,
        String sourceKey,
        Long spaId,
        Long userId,
        Long bookingId,
        Long subscriptionId,
        Long originalDocumentId,
        LocalDate periodStart,
        LocalDate periodEnd,
        String supplierName,
        String supplierTradeName,
        String supplierAddress,
        String supplierGstin,
        String supplierState,
        String supplierStateCode,
        String recipientName,
        String recipientAddress,
        String recipientGstin,
        String recipientEmail,
        String recipientPhone,
        String placeOfSupply,
        String placeOfSupplyCode,
        String description,
        String sacCode,
        Integer quantity,
        String currency,
        Long grossCents,
        Long discountCents,
        Long taxableCents,
        Integer rateBps,
        Long cgstCents,
        Long sgstCents,
        Long igstCents,
        Long totalCents,
        Long commissionBaseCents,
        BigDecimal commissionPct,
        String notes,
        LocalDateTime createdAt
) {
    public static final String TAX_INVOICE = "TAX_INVOICE";
    public static final String BILL_OF_SUPPLY = "BILL_OF_SUPPLY";
    public static final String CREDIT_NOTE = "CREDIT_NOTE";
    public static final String COMMISSION_INVOICE = "COMMISSION_INVOICE";

    public long taxCents() {
        return n(cgstCents) + n(sgstCents) + n(igstCents);
    }

    public boolean intraState() {
        return n(igstCents) == 0;
    }

    /** What the document is called at its top. */
    public String title() {
        return switch (docType) {
            case BILL_OF_SUPPLY -> "Bill of Supply";
            case CREDIT_NOTE -> "Credit Note";
            default -> "Tax Invoice";
        };
    }

    private static long n(Long v) {
        return v == null ? 0 : v;
    }
}
