package com.nirvana.application.service;

import com.nirvana.application.model.dto.InvoiceResponseDto;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The guest's invoice as an A4 PDF: a branded header with the Nirvana mark, who was billed and by
 * which spa, the appointment, the line item and totals, and how it was paid. Uses the PDF's
 * built-in fonts, which have no rupee sign, so amounts read "INR 2,500.00".
 */
public final class InvoicePdf {
    private InvoicePdf() {}

    // The site's brand colours (see frontend/public/nirvana.svg).
    private static final Color FOREST = new Color(0x20, 0x35, 0x23);
    private static final Color LIME = new Color(0xE5, 0xF3, 0xA7);
    private static final Color INK = new Color(0x1F, 0x29, 0x37);
    private static final Color MUTED = new Color(0x64, 0x74, 0x8B);
    private static final Color LINE = new Color(0xE2, 0xE8, 0xF0);
    private static final Color TINT = new Color(0xF3, 0xF7, 0xEE);
    private static final Color WHITE = Color.WHITE;

    private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private static final float W = PDRectangle.A4.getWidth(), H = PDRectangle.A4.getHeight();
    private static final float M = 48;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM yyyy, h:mm a z", Locale.ENGLISH);

    /** Characters the built-in fonts can't draw, swapped for ones they can. */
    private static final Map<Integer, String> SWAPS = Map.of(0x20B9, "Rs ", 0x2013, "-", 0x2014, "-", 0x2018, "'",
            0x2019, "'", 0x201C, "\"", 0x201D, "\"", 0x2026, "...", 0x00A0, " ");

    public static byte[] render(InvoiceResponseDto invoice) throws IOException {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDDocumentInformation info = document.getDocumentInformation();
            info.setTitle("Invoice " + invoice.invoiceNumber());
            info.setAuthor("Nirvana Wellness");
            try (var c = new PDPageContentStream(document, page)) {
                new Drawing(c, invoice).draw();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    /** One invoice page, drawn top to bottom. */
    private record Drawing(PDPageContentStream c, InvoiceResponseDto inv) {
        void draw() throws IOException {
            header();
            float y = summary(H - 150);
            y = parties(y - 22);
            y = appointment(y - 18);
            y = items(y - 22);
            totals(y - 8);
            footer();
        }

        private void header() throws IOException {
            fillRect(0, H - 118, W, 118, FOREST);
            // The Nirvana mark: a rounded square with "n." as on the website.
            float s = 46, x = M, y = H - 82;
            roundedRect(x, y, s, s, 13, LIME);
            text("n.", BOLD, 30, FOREST, x + 7, y + 11);
            text("Nirvana", BOLD, 22, WHITE, x + s + 14, y + 22);
            text("WELLNESS", REGULAR, 8.5f, LIME, x + s + 15, y + 8);
            textRight("INVOICE", BOLD, 22, WHITE, W - M, y + 22);
            textRight(inv.invoiceNumber(), REGULAR, 10, LIME, W - M, y + 8);
        }

        /** Status pill, then date, booking reference and payment in a row. */
        private float summary(float y) throws IOException {
            String[] status = status();
            float pw = width(status[0], BOLD, 9) + 22;
            roundedRect(W - M - pw, y - 4, pw, 20, 10, status[1].equals("ok") ? new Color(0xDC, 0xFC, 0xE7)
                    : status[1].equals("warn") ? new Color(0xFE, 0xF3, 0xC7) : new Color(0xFE, 0xE2, 0xE2));
            text(status[0], BOLD, 9, status[1].equals("ok") ? new Color(0x16, 0x65, 0x34)
                    : status[1].equals("warn") ? new Color(0x92, 0x40, 0x0E) : new Color(0x99, 0x1B, 0x1B), W - M - pw + 11, y + 2.5f);
            text("Invoice for your Nirvana booking", BOLD, 15, INK, M, y);
            float row = y - 40, col = (W - 2 * M) / 3;
            label("INVOICE DATE", M, row);
            value(inv.issuedAt() == null ? "-" : inv.issuedAt().atZoneSameInstant(zone()).format(DATE), M, row - 15);
            label("BOOKING REFERENCE", M + col, row);
            value(inv.bookingReference(), M + col, row - 15);
            label("PAYMENT", M + 2 * col, row);
            value(paymentLine(), M + 2 * col, row - 15);
            float end = row - 32;
            if (!blank(inv.paymentReference()) && !"OFFLINE".equals(inv.paymentMode())) {
                text("Ref " + inv.paymentReference(), REGULAR, 8.5f, MUTED, M + 2 * col, row - 28);
                end = row - 42;
            }
            hr(end);
            return end;
        }

        /** "Billed to" and "Spa" side by side. */
        private float parties(float top) throws IOException {
            float gap = 16, w = (W - 2 * M - gap) / 2;
            List<String> guest = new ArrayList<>();
            guest.add(blank(inv.userName()) ? "Guest" : inv.userName());
            if (!blank(inv.userEmail())) guest.add(inv.userEmail());
            if (!blank(inv.userPhone())) guest.add(inv.userPhone());
            List<String> spa = new ArrayList<>();
            spa.add(inv.spaName());
            if (!blank(inv.spaAddress())) spa.addAll(wrap(inv.spaAddress(), REGULAR, 9.5f, w - 28, 3));
            if (!blank(inv.spaPhone())) spa.add(inv.spaPhone());
            float h = 30 + 15 * Math.max(guest.size(), spa.size()) + 8;
            card(M, top - h, w, h, "BILLED TO", guest);
            card(M + w + gap, top - h, w, h, "SPA", spa);
            return top - h;
        }

        private float appointment(float top) throws IOException {
            float h = 52;
            roundedRect(M, top - h, W - 2 * M, h, 10, TINT);
            label("APPOINTMENT", M + 14, top - 18);
            String when = inv.appointmentStart() == null ? "-" : inv.appointmentStart().atZoneSameInstant(zone()).format(WHEN);
            text(when, BOLD, 11.5f, INK, M + 14, top - 36);
            List<String> bits = new ArrayList<>();
            if (inv.durationMinutes() != null && inv.durationMinutes() > 0) bits.add(inv.durationMinutes() + " min");
            int guests = inv.guestCount() == null ? 1 : inv.guestCount();
            bits.add(guests + (guests == 1 ? " guest" : " guests"));
            textRight(String.join("  |  ", bits), REGULAR, 10, MUTED, W - M - 14, top - 36);
            return top - h;
        }

        /** The table header and the one line item. */
        private float items(float top) throws IOException {
            float right = W - M, qtyX = right - 150;
            fillRect(M, top - 24, W - 2 * M, 24, FOREST);
            text("DESCRIPTION", BOLD, 8.5f, LIME, M + 12, top - 15.5f);
            textRight("QTY", BOLD, 8.5f, LIME, qtyX, top - 15.5f);
            textRight("AMOUNT", BOLD, 8.5f, LIME, right - 12, top - 15.5f);
            float y = top - 24 - 22;
            text(inv.serviceName(), BOLD, 10.5f, INK, M + 12, y);
            text("Treatment at " + inv.spaName(), REGULAR, 9, MUTED, M + 12, y - 13);
            textRight(String.valueOf(inv.guestCount() == null ? 1 : inv.guestCount()), REGULAR, 10.5f, INK, qtyX, y);
            textRight(money(inv.amountCents()), REGULAR, 10.5f, INK, right - 12, y);
            hr(y - 26);
            return y - 26;
        }

        private void totals(float top) throws IOException {
            float right = W - M - 12, labelX = W - M - 230;
            float y = top - 16;
            y = totalRow("Subtotal", money(inv.amountCents()), labelX, right, y, false);
            if (cents(inv.discountCents()) > 0)
                y = totalRow("Discount", "- " + money(inv.discountCents()), labelX, right, y, false);
            y = totalRow(taxLabel(), money(inv.taxCents()), labelX, right, y, false);
            y -= 4;
            roundedRect(labelX - 12, y - 12, right - labelX + 24, 30, 8, FOREST);
            text("Total", BOLD, 12, WHITE, labelX, y);
            textRight(money(inv.totalCents()), BOLD, 12, LIME, right, y);
            y -= 34;
            if (cents(inv.refundedCents()) > 0) y = totalRow("Refunded", "- " + money(inv.refundedCents()), labelX, right, y, false);
            String paidLabel = "OFFLINE".equals(inv.paymentMode()) && !"COMPLETED".equals(inv.bookingStatus()) ? "Due at the spa" : "Amount paid";
            int paid = "OFFLINE".equals(inv.paymentMode()) && !"COMPLETED".equals(inv.bookingStatus())
                    ? cents(inv.totalCents()) : Math.max(0, cents(inv.totalCents()) - cents(inv.refundedCents()));
            totalRow(paidLabel, money(paid), labelX, right, y, true);
        }

        private float totalRow(String label, String amount, float x, float right, float y, boolean strong) throws IOException {
            text(label, strong ? BOLD : REGULAR, 10, strong ? INK : MUTED, x, y);
            textRight(amount, strong ? BOLD : REGULAR, 10, INK, right, y);
            return y - 18;
        }

        private void footer() throws IOException {
            hr(92);
            text("Thank you for booking with Nirvana.", BOLD, 10.5f, INK, M, 72);
            text("This is a computer-generated invoice and needs no signature. Quote booking "
                    + inv.bookingReference() + " if you contact us.", REGULAR, 8.5f, MUTED, M, 57);
            textRight("Nirvana Wellness", BOLD, 8.5f, FOREST, W - M, 72);
        }

        // ---- What the page says.

        /** The status pill's text and tone: ok, warn or bad. */
        private String[] status() {
            int total = cents(inv.totalCents()), refunded = cents(inv.refundedCents());
            if (refunded > 0 && refunded >= total) return new String[]{"REFUNDED", "bad"};
            if (refunded > 0) return new String[]{"PARTLY REFUNDED", "warn"};
            if ("CANCELLED".equals(inv.bookingStatus())) return new String[]{"CANCELLED", "bad"};
            if ("OFFLINE".equals(inv.paymentMode()))
                return "COMPLETED".equals(inv.bookingStatus()) ? new String[]{"PAID AT SPA", "ok"} : new String[]{"DUE AT SPA", "warn"};
            if ("CAPTURED".equals(inv.paymentStatus()) || "COMPLETED".equals(inv.paymentStatus()) || "REFUNDED".equals(inv.paymentStatus()))
                return new String[]{"PAID", "ok"};
            return new String[]{"AWAITING PAYMENT", "warn"};
        }

        private String paymentLine() {
            if ("OFFLINE".equals(inv.paymentMode())) return "Pay at spa";
            String paid = inv.paidAt() == null ? "" : " on " + inv.paidAt().atZoneSameInstant(zone()).format(DATE);
            return "Razorpay" + paid;
        }

        private String taxLabel() {
            int base = cents(inv.amountCents()) - cents(inv.discountCents()), tax = cents(inv.taxCents());
            if (base <= 0 || tax <= 0) return "GST";
            BigDecimal rate = BigDecimal.valueOf(tax * 100L).divide(BigDecimal.valueOf(base), 1, RoundingMode.HALF_UP).stripTrailingZeros();
            return "GST (" + rate.toPlainString() + "%)";
        }

        private String money(Integer minor) {
            NumberFormat f = NumberFormat.getNumberInstance(new Locale("en", "IN"));
            f.setMinimumFractionDigits(2);
            f.setMaximumFractionDigits(2);
            return (blank(inv.currency()) ? "INR" : inv.currency()) + " " + f.format(cents(minor) / 100.0);
        }

        private ZoneId zone() {
            try {
                return blank(inv.spaTimezone()) ? ZoneId.of("Asia/Kolkata") : ZoneId.of(inv.spaTimezone());
            } catch (RuntimeException e) {
                return ZoneId.of("Asia/Kolkata");
            }
        }

        // ---- Drawing helpers.

        private void card(float x, float y, float w, float h, String title, List<String> lines) throws IOException {
            strokeRoundedRect(x, y, w, h, 10, LINE);
            label(title, x + 14, y + h - 18);
            float ty = y + h - 34;
            for (int i = 0; i < lines.size(); i++) {
                text(lines.get(i), i == 0 ? BOLD : REGULAR, i == 0 ? 10.5f : 9.5f, i == 0 ? INK : MUTED, x + 14, ty);
                ty -= 15;
            }
        }

        private void label(String s, float x, float y) throws IOException {
            text(s, BOLD, 7.5f, MUTED, x, y);
        }

        private void value(String s, float x, float y) throws IOException {
            text(s, BOLD, 10.5f, INK, x, y);
        }

        private void hr(float y) throws IOException {
            c.setStrokingColor(LINE);
            c.setLineWidth(0.8f);
            c.moveTo(M, y);
            c.lineTo(W - M, y);
            c.stroke();
        }

        private void text(String s, PDFont font, float size, Color color, float x, float y) throws IOException {
            c.beginText();
            c.setFont(font, size);
            c.setNonStrokingColor(color);
            c.newLineAtOffset(x, y);
            c.showText(safe(s, font));
            c.endText();
        }

        private void textRight(String s, PDFont font, float size, Color color, float right, float y) throws IOException {
            text(s, font, size, color, right - width(s, font, size), y);
        }

        private void fillRect(float x, float y, float w, float h, Color color) throws IOException {
            c.setNonStrokingColor(color);
            c.addRect(x, y, w, h);
            c.fill();
        }

        private void roundedRect(float x, float y, float w, float h, float r, Color fill) throws IOException {
            c.setNonStrokingColor(fill);
            roundedPath(x, y, w, h, r);
            c.fill();
        }

        private void strokeRoundedRect(float x, float y, float w, float h, float r, Color stroke) throws IOException {
            c.setStrokingColor(stroke);
            c.setLineWidth(1f);
            roundedPath(x, y, w, h, r);
            c.stroke();
        }

        /** A rectangle with rounded corners, from four Bezier quarter-circles. */
        private void roundedPath(float x, float y, float w, float h, float r) throws IOException {
            float k = 0.5523f * r;
            c.moveTo(x + r, y);
            c.lineTo(x + w - r, y);
            c.curveTo(x + w - r + k, y, x + w, y + r - k, x + w, y + r);
            c.lineTo(x + w, y + h - r);
            c.curveTo(x + w, y + h - r + k, x + w - r + k, y + h, x + w - r, y + h);
            c.lineTo(x + r, y + h);
            c.curveTo(x + r - k, y + h, x, y + h - r + k, x, y + h - r);
            c.lineTo(x, y + r);
            c.curveTo(x, y + r - k, x + r - k, y, x + r, y);
            c.closePath();
        }
    }

    // ---- Text measuring and cleaning.

    static String safe(String s, PDFont font) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        s.codePoints().forEach(cp -> {
            String ch = SWAPS.getOrDefault(cp, new String(Character.toChars(cp)));
            try {
                font.encode(ch);
                out.append(ch);
            } catch (IOException | IllegalArgumentException unsupported) {
                out.append('?');
            }
        });
        return out.toString();
    }

    static float width(String s, PDFont font, float size) throws IOException {
        return font.getStringWidth(safe(s, font)) / 1000f * size;
    }

    static List<String> wrap(String s, PDFont font, float size, float max, int maxLines) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.trim().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (width(candidate, font, size) <= max || line.isEmpty()) {
                line.setLength(0);
                line.append(candidate);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        if (lines.size() > maxLines) {
            lines = new ArrayList<>(lines.subList(0, maxLines));
            lines.set(maxLines - 1, lines.get(maxLines - 1) + "...");
        }
        return lines;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static int cents(Integer v) {
        return v == null ? 0 : v;
    }
}
