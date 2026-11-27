// src/main/java/com/nirvana/application/service/impl/InvoiceServiceImpl.java
package com.nirvana.application.service.impl;

import com.nirvana.application.model.*;
import com.nirvana.application.model.dto.InvoiceEmailDto;
import com.nirvana.application.model.dto.InvoiceResponseDto;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.PaymentStatus;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.InvoiceRepository;
import com.nirvana.application.repository.PaymentRepository;
import com.nirvana.application.service.EmailService;
import com.nirvana.application.service.InvoiceService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final EmailService emailService;   // you can provide a NoOp impl for now
    private final com.nirvana.application.service.gst.GstInvoiceService gstInvoices;


    @Override
    @Transactional
    public InvoiceResponseDto generateInvoiceForBooking(Long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));

        // The spa's GST invoice (or bill of supply), issued once and emailed to the guest on commit.
        var gst = gstInvoices.issueForBooking(bookingId);

        // Idempotent: if already exists, return existing
        var existing = invoiceRepository.findByBookingId(bookingId);
        if (existing.isPresent()) {
            return toDto(existing.get());
        }

        Payment payment = paymentRepository
                .findFirstByBookingIdAndPaymentStatusIn(
                        bookingId,
                        List.of(PaymentStatus.COMPLETED, PaymentStatus.CAPTURED, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED))
                .orElseThrow(() -> new IllegalStateException(
                        "No successful payment found for booking " + bookingId));

        // Build invoice monetary fields
        int amountCents = booking.getPriceCents();         // base price
        int taxCents = booking.getTaxCents();
        int discountCents = booking.getDiscountCents();
        int totalCents = payment.getAmountCents();

        // The GST document's number, so the receipt and the invoice agree.
        String invoiceNumber = gst.map(com.nirvana.application.service.gst.GstDocument::documentNumber)
                .orElseGet(this::generateInvoiceNumber);

        Invoice invoice = Invoice.builder()
                .invoiceNumber(invoiceNumber)
                .booking(booking)
                .user(booking.getUser())
                .spa(booking.getSpa())
                .status("PAID")
                .amountCents(amountCents)
                .taxCents(taxCents)
                .discountCents(discountCents)
                .totalCents(totalCents)
                .currency(booking.getCurrency())
                .issuedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .pdfUrl(buildPdfUrl(bookingId))
                .metaJson(null)
                .build();

        Invoice saved = invoiceRepository.save(invoice);

        // The GST document's own email covers the guest; this plain one is only for a booking without one.
        if (gst.isEmpty()) try {
            InvoiceEmailDto emailDto = buildEmailDto(saved);
            emailService.sendInvoiceEmail(emailDto);
        } catch (Exception e) {
            // Don't break invoice creation if email fails
            log.error("Failed to send invoice email for booking {} errorType={}", bookingId, e.getClass().getSimpleName());
        }

        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponseDto getInvoiceForBooking(Long bookingId) {
        Invoice invoice = invoiceRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found for booking " + bookingId));
        return toDto(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponseDto getByInvoiceNumber(String invoiceNumber) {
        Invoice invoice = invoiceRepository.findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found: " + invoiceNumber));
        return toDto(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public Invoice getInvoiceEntityByBookingId(Long bookingId) {
        return invoiceRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found for booking " + bookingId));
    }

    // ---------- helpers ----------

    private String generateInvoiceNumber() {
        // Simple robust generator: INV-2025-8CHAR
        // You can replace with sequential series using a DB sequence table.
        String year = String.valueOf(OffsetDateTime.now(ZoneOffset.UTC).getYear());
        while (true) {
            String random = UUID.randomUUID().toString()
                    .replace("-", "")
                    .substring(0, 8)
                    .toUpperCase();
            String candidate = "INV-" + year + "-" + random;
            if (!invoiceRepository.existsByInvoiceNumber(candidate)) {
                return candidate;
            }
        }
    }

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private String buildPdfUrl(Long bookingId) {
        return "/api/bookings/" + bookingId + "/invoice/pdf";
    }

    private InvoiceResponseDto toDto(Invoice invoice) {
        Booking b = invoice.getBooking();
        Spa spa = b.getSpa();
        User user = b.getUser();
        com.nirvana.application.model.Service service = b.getService();
        Payment payment = paymentRepository.findTopByBookingIdOrderByCreatedAtDesc(b.getId()).orElse(null);
        Address address = spa.getAddress();
        String spaAddress = address == null ? null
                : address.getFormattedAddress() != null && !address.getFormattedAddress().isBlank() ? address.getFormattedAddress()
                : java.util.stream.Stream.of(address.getAddressLine(), address.getCity(), address.getState(), address.getPostalCode())
                        .filter(v -> v != null && !v.isBlank()).collect(java.util.stream.Collectors.joining(", "));

        var gst = gstInvoices.bySource("BOOKING:" + b.getId()).orElse(null);
        return InvoiceResponseDto.builder()
                .documentTitle(gst == null ? null : gst.title())
                .supplierName(gst == null ? null : gst.supplierName())
                .supplierGstin(gst == null ? null : gst.supplierGstin())
                .sacCode(gst == null ? null : gst.sacCode())
                .placeOfSupply(gst == null || gst.placeOfSupply() == null ? null
                        : gst.placeOfSupply() + (gst.placeOfSupplyCode() == null ? "" : " (" + gst.placeOfSupplyCode() + ")"))
                .cgstCents(gst == null ? null : gst.cgstCents())
                .sgstCents(gst == null ? null : gst.sgstCents())
                .igstCents(gst == null ? null : gst.igstCents())
                .rateLabel(gst == null ? null : com.nirvana.application.service.gst.GstMath.percent(gst.rateBps()))
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .bookingId(b.getId())
                .bookingReference(b.getBookingReference())
                .spaId(spa.getId())
                .spaName(spa.getName())
                .userId(user.getId())
                .userName(user.getName())
                .userEmail(user.getEmail())
                .userPhone(user.getPhone())
                .serviceId(service.getId())
                .serviceName(service.getName())
                .amountCents(invoice.getAmountCents())
                .taxCents(invoice.getTaxCents())
                .discountCents(invoice.getDiscountCents())
                .refundedCents(payment == null || payment.getRefundedCents() == null ? 0 : payment.getRefundedCents())
                .totalCents(invoice.getTotalCents())
                .currency(invoice.getCurrency())
                .issuedAt(invoice.getIssuedAt())
                .pdfUrl(buildPdfUrl(b.getId()))
                .spaAddress(spaAddress)
                .spaPhone(spa.getPhone())
                .spaTimezone(spa.getTimezone())
                .appointmentStart(b.getStartTs())
                .durationMinutes(service.getDurationMinutes() != null ? service.getDurationMinutes() : service.getDurationMin())
                .guestCount(b.getGuestCount())
                .paymentMode(b.getPaymentMode() == null ? null : b.getPaymentMode().name())
                .bookingStatus(b.getStatus() == null ? null : b.getStatus().name())
                .paymentStatus(payment == null || payment.getPaymentStatus() == null ? null : payment.getPaymentStatus().name())
                .paymentReference(payment == null ? null : payment.getTransactionId())
                .paidAt(payment == null ? null : payment.getCapturedAt())
                .build();
    }

    private InvoiceEmailDto buildEmailDto(Invoice invoice) {
        Booking b = invoice.getBooking();
        Spa spa = b.getSpa();
        User user = b.getUser();
        com.nirvana.application.model.Service service = b.getService();

        Address addr = spa.getAddress();

        return InvoiceEmailDto.builder()
                .invoiceNumber(invoice.getInvoiceNumber())
                .issuedAt(invoice.getIssuedAt())
                .toEmail(user.getEmail())
                .toName(user.getName())
                .spaName(spa.getName())
                .spaAddressLine1(addr != null ? addr.getAddressLine() : null)
                .spaAddressLine2(addr != null ? addr.getAddressLine2() : null)
                .spaCity(addr != null ? addr.getCity() : null)
                .spaState(addr != null ? addr.getState() : null)
                .spaCountry(addr != null ? addr.getCountry() : null)
                .spaPostalCode(addr != null ? addr.getPostalCode() : null)
                .spaPhone(spa.getPhone())
                .spaTimezone(spa.getTimezone())
                .bookingReference(b.getBookingReference())
                .bookingStartTs(b.getStartTs())
                .bookingEndTs(b.getEndTs())
                .serviceName(service.getName())
                .guestCount(b.getGuestCount())
                .amountCents(invoice.getAmountCents())
                .taxCents(invoice.getTaxCents())
                .discountCents(invoice.getDiscountCents())
                .totalCents(invoice.getTotalCents())
                .currency(invoice.getCurrency())
                .invoicePdfUrl(frontendUrl.replaceAll("/+$", "") + "/me/bookings")
                .build();
    }
}
