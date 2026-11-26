// src/main/java/com/nirvana/application/model/dto/InvoiceEmailDto.java
package com.nirvana.application.model.dto;

import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record InvoiceEmailDto(
        String invoiceNumber,
        OffsetDateTime issuedAt,

        String toEmail,
        String toName,

        String spaName,
        String spaAddressLine1,
        String spaAddressLine2,
        String spaCity,
        String spaState,
        String spaCountry,
        String spaPostalCode,
        String spaPhone,

        String bookingReference,
        OffsetDateTime bookingStartTs,
        OffsetDateTime bookingEndTs,
        String serviceName,
        Integer guestCount,

        Integer amountCents,
        Integer taxCents,
        Integer discountCents,
        Integer totalCents,
        String currency,

        String invoicePdfUrl,

        /** The spa's time zone, so the appointment time reads as the guest will see it on arrival. */
        String spaTimezone,

        // GST details, when the email is for a GST document (tax invoice, bill of supply or credit note).
        /** "Tax Invoice", "Bill of Supply" or "Credit Note"; null for the older plain invoice email. */
        String documentTitle,
        /** The spa's legal name: it, not Nirvana, is the supplier. */
        String supplierName,
        String supplierGstin,
        /** For a credit note, the invoice it credits. */
        String originalDocumentNumber,
        Long taxableCents,
        Long cgstCents,
        Long sgstCents,
        Long igstCents,
        /** Such as "18%". */
        String rateLabel
) {}
