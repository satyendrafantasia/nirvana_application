package com.nirvana.application.service;

import com.nirvana.application.model.dto.InvoiceResponseDto;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import java.io.*;
import java.math.BigDecimal;

public final class InvoicePdf {
    private InvoicePdf() {}

    public static byte[] render(InvoiceResponseDto invoice) throws IOException {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage();
            document.addPage(page);
            var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (var content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(font, 14);
                content.setLeading(24);
                content.newLineAtOffset(50, 710);
                for (String line : new String[]{"Nirvana Wellness", "Invoice " + invoice.invoiceNumber(),
                        "Issued " + invoice.issuedAt().toLocalDate(), "Booking " + invoice.bookingReference(),
                        "Spa: " + invoice.spaName(), "Service: " + invoice.serviceName(),
                        "Refunded: " + invoice.currency() + " " + BigDecimal.valueOf(invoice.refundedCents(), 2),
                        "Amount paid: " + invoice.currency() + " " + BigDecimal.valueOf(invoice.totalCents(), 2)}) {
                    content.showText(line.replaceAll("[^ -~]", "?"));
                    content.newLine();
                }
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
