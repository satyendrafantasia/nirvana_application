package com.nirvana.application;

import com.nirvana.application.model.dto.InvoiceResponseDto;
import com.nirvana.application.service.InvoicePdf;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** The printed invoice: what it says for each way a booking can be paid. */
class InvoicePdfTest {

    private static InvoiceResponseDto.InvoiceResponseDtoBuilder invoice() {
        return InvoiceResponseDto.builder()
                .invoiceNumber("INV-2026-23D2E061").bookingReference("7451CD21648D")
                .spaName("The Fern House · Gomti Nagar").spaAddress("12 Vibhuti Khand, Gomti Nagar, Lucknow, Uttar Pradesh 226010, India")
                .spaPhone("+91 98765 43210").spaTimezone("Asia/Kolkata")
                .userName("Asha Verma").userEmail("asha@example.test").userPhone("+91 90000 00001")
                .serviceName("Aromatherapy Massage").durationMinutes(60).guestCount(1)
                .amountCents(211864).taxCents(38136).discountCents(0).totalCents(250000).refundedCents(0).currency("INR")
                .issuedAt(OffsetDateTime.parse("2026-11-22T05:00:00Z"))
                .appointmentStart(OffsetDateTime.parse("2026-11-22T07:30:00Z"));
    }

    private static String text(InvoiceResponseDto dto, String name) throws Exception {
        byte[] pdf = InvoicePdf.render(dto);
        try (var doc = Loader.loadPDF(pdf)) {
            assertEquals(1, doc.getNumberOfPages());
            // Set -Dinvoice.preview.dir=... to save each sample as an image for a visual check.
            String dir = System.getProperty("invoice.preview.dir");
            if (dir != null) ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0, 110), "png", new File(dir, name + ".png"));
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test void paidOnline() throws Exception {
        String t = text(invoice().paymentMode("ONLINE").paymentStatus("CAPTURED").bookingStatus("CONFIRMED")
                .paymentReference("pay_U3666ubie3mWA2").paidAt(OffsetDateTime.parse("2026-11-22T04:58:00Z")).build(), "paid-online");
        for (String expected : new String[]{"Nirvana", "INVOICE", "INV-2026-23D2E061", "PAID", "7451CD21648D", "Razorpay on 22 Nov 2026", "Ref pay_U3666ubie3mWA2",
                "Asha Verma", "The Fern House · Gomti Nagar", "Sun 22 Nov 2026, 1:00 PM IST", "60 min", "Aromatherapy Massage",
                "INR 2,118.64", "GST (18%)", "INR 381.36", "INR 2,500.00", "Amount paid"})
            assertTrue(t.contains(expected), "missing \"" + expected + "\" in:\n" + t);
    }

    @Test void payAtSpaBeforeTheVisit() throws Exception {
        String t = text(invoice().paymentMode("OFFLINE").bookingStatus("CONFIRMED").build(), "pay-at-spa");
        assertTrue(t.contains("DUE AT SPA"), t);
        assertTrue(t.contains("Pay at spa"), t);
        assertTrue(t.contains("Due at the spa"), t);
        assertFalse(t.contains("Tax invoice"), "no GSTIN on file, so not titled a tax invoice");
        assertFalse(t.contains("Amount paid"), t);
    }

    @Test void refunded() throws Exception {
        String t = text(invoice().paymentMode("ONLINE").paymentStatus("REFUNDED").bookingStatus("CANCELLED")
                .refundedCents(250000).discountCents(10000).build(), "refunded");
        assertTrue(t.contains("REFUNDED"), t);
        assertTrue(t.contains("Refunded"), t);
        assertTrue(t.contains("Discount"), t);
        assertTrue(t.contains("INR 0.00"), "nothing kept after a full refund:\n" + t);
    }

    @Test void oddCharactersDoNotBreakTheInvoice() throws Exception {
        String t = text(invoice().spaName("Spa ₹ “Zen” – Café").paymentMode("OFFLINE").build(), "odd-characters");
        assertTrue(t.contains("Spa Rs  \"Zen\" - Café"), t);
    }
}
