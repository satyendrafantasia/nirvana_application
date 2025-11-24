// src/main/java/com/nirvana/application/service/InvoiceService.java
package com.nirvana.application.service;

import com.nirvana.application.model.Invoice;
import com.nirvana.application.model.dto.InvoiceResponseDto;

public interface InvoiceService {

    /**
     * Idempotent:
     * - If invoice already exists for booking → return existing.
     * - Else create new invoice based on Booking + successful Payment.
     */
    InvoiceResponseDto generateInvoiceForBooking(Long bookingId);

    /**
     * Get invoice for booking (if exists), without generating.
     */
    InvoiceResponseDto getInvoiceForBooking(Long bookingId);

    /**
     * Get invoice by invoice number.
     */
    InvoiceResponseDto getByInvoiceNumber(String invoiceNumber);

    /**
     * Internal raw access.
     */
    Invoice getInvoiceEntityByBookingId(Long bookingId);
}
