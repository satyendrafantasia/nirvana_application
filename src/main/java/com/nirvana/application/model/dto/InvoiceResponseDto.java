// src/main/java/com/nirvana/application/model/dto/InvoiceResponseDto.java
package com.nirvana.application.model.dto;

import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record InvoiceResponseDto(
        Long id,
        String invoiceNumber,
        Long bookingId,
        String bookingReference,
        Long spaId,
        String spaName,
        Long userId,
        String userName,
        String userEmail,
        String userPhone,
        Long serviceId,
        String serviceName,

        Integer amountCents,
        Integer taxCents,
        Integer discountCents,
        Integer totalCents,
        Integer refundedCents,
        String currency,

        OffsetDateTime issuedAt,
        String pdfUrl,

        // For the printed invoice.
        String spaAddress,
        String spaPhone,
        String spaTimezone,
        OffsetDateTime appointmentStart,
        Integer durationMinutes,
        Integer guestCount,
        /** ONLINE or OFFLINE (pay at spa). */
        String paymentMode,
        String bookingStatus,
        String paymentStatus,
        /** The gateway's payment ID, such as Razorpay's pay_…, when paid online. */
        String paymentReference,
        OffsetDateTime paidAt,

        // From the GST document, when one was issued.
        /** "Tax Invoice" or "Bill of Supply"; null for an invoice from before GST documents. */
        String documentTitle,
        /** The spa's legal name and GSTIN: the spa, not Nirvana, is the supplier. */
        String supplierName,
        String supplierGstin,
        String sacCode,
        String placeOfSupply,
        Long cgstCents,
        Long sgstCents,
        Long igstCents,
        /** Such as "18%". */
        String rateLabel,
        /** The add-ons on the booking; amountCents is the treatment plus these. */
        java.util.List<com.nirvana.application.service.addons.BookingAddOns.Line> addOns
) {}
