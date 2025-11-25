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

    @Value("${invoice.pdf-base-url:https://cdn.nirvana.invalid/invoices/}")
    private String invoicePdfBaseUrl;

    @Override
    @Transactional
    public InvoiceResponseDto generateInvoiceForBooking(Long bookingId) {
        // Idempotent: if already exists, return existing
        var existing = invoiceRepository.findByBookingId(bookingId);
        if (existing.isPresent()) {
            return toDto(existing.get());
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Cannot issue invoice for non-CONFIRMED booking: " + booking.getStatus());
        }

        Payment payment = paymentRepository
                .findFirstByBookingIdAndPaymentStatusIn(
                        bookingId,
                        List.of(PaymentStatus.COMPLETED, PaymentStatus.CAPTURED))
                .orElseThrow(() -> new IllegalStateException(
                        "No successful payment found for booking " + bookingId));

        // Build invoice monetary fields
        int amountCents = booking.getPriceCents();         // base price
        int taxCents = booking.getTaxCents();
        int discountCents = booking.getDiscountCents();
        int totalCents = amountCents + taxCents - discountCents;

        String invoiceNumber = generateInvoiceNumber();

        Invoice invoice = Invoice.builder()
                .invoiceNumber(invoiceNumber)
                .booking(booking)
                .amountCents(amountCents)
                .taxCents(taxCents)
                .discountCents(discountCents)
                .totalCents(totalCents)
                .currency(booking.getCurrency())
                .issuedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .pdfUrl(buildPdfUrl(invoiceNumber))
                .metaJson(null)
                .build();

        Invoice saved = invoiceRepository.save(invoice);

        // Build email payload and send (non-blocking in real prod via async queue)
        try {
            InvoiceEmailDto emailDto = buildEmailDto(saved);
            emailService.sendInvoiceEmail(emailDto);
        } catch (Exception e) {
            // Don't break invoice creation if email fails
            log.error("Failed to send invoice email for booking {}", bookingId, e);
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

    private String buildPdfUrl(String invoiceNumber) {
        if (invoicePdfBaseUrl.endsWith("/")) {
            return invoicePdfBaseUrl + invoiceNumber + ".pdf";
        }
        return invoicePdfBaseUrl + "/" + invoiceNumber + ".pdf";
    }

    private InvoiceResponseDto toDto(Invoice invoice) {
        Booking b = invoice.getBooking();
        Spa spa = b.getSpa();
        User user = b.getUser();
        com.nirvana.application.model.Service service = b.getService();

        return InvoiceResponseDto.builder()
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
                .totalCents(invoice.getTotalCents())
                .currency(invoice.getCurrency())
                .issuedAt(invoice.getIssuedAt())
                .pdfUrl(invoice.getPdfUrl())
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
                .invoicePdfUrl(invoice.getPdfUrl())
                .build();
    }
}
