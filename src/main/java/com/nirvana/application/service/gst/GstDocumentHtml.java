package com.nirvana.application.service.gst;

import org.springframework.web.util.HtmlUtils;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * A GST document as a self-contained, printable HTML page (A4; the browser's "Save as PDF" gives a
 * PDF). It carries what Rule 46 (tax invoice), Rule 49 (bill of supply) and Rule 53 (credit note) of
 * the CGST Rules ask for: supplier and GSTIN, number and date, recipient, place of supply, SAC,
 * taxable value, rate and the CGST, SGST or IGST on it.
 */
public final class GstDocumentHtml {
    private GstDocumentHtml() {}

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    public static String render(GstDocument d, GstDocument original) {
        boolean taxed = d.taxCents() > 0 || (d.rateBps() != null && d.rateBps() > 0 && d.supplierGstin() != null);
        boolean intra = d.intraState();
        String half = d.rateBps() == null ? "" : GstMath.percent(d.rateBps() / 2);
        String full = d.rateBps() == null ? "" : GstMath.percent(d.rateBps());
        StringBuilder h = new StringBuilder(8_000);
        h.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>").append(e(d.title() + " " + d.documentNumber())).append("</title><style>").append(CSS).append("</style></head><body>")
                .append("<div class=\"bar\"><button onclick=\"window.print()\">Print or save as PDF</button></div><main>");

        // Heading.
        h.append("<header><div><h1>").append(e(d.title())).append("</h1>");
        if (GstDocument.CREDIT_NOTE.equals(d.docType())) h.append("<p class=\"muted\">Issued under section 34 of the CGST Act</p>");
        else if (GstDocument.BILL_OF_SUPPLY.equals(d.docType())) h.append("<p class=\"muted\">Supplier not registered under GST: no GST charged</p>");
        h.append("</div><table class=\"meta\">")
                .append(row(GstDocument.CREDIT_NOTE.equals(d.docType()) ? "Credit note no." : d.title() + " no.", d.documentNumber()))
                .append(row("Date", d.issueDate().format(DATE)));
        if (original != null) {
            h.append(row("Against " + original.title().toLowerCase(Locale.ROOT), original.documentNumber() + " of " + original.issueDate().format(DATE)));
        }
        if (d.periodStart() != null) h.append(row("Period", d.periodStart().format(DATE) + " to " + d.periodEnd().format(DATE)));
        h.append(row("Place of supply", d.placeOfSupply() == null ? "-" : d.placeOfSupply()
                + (d.placeOfSupplyCode() == null ? "" : " (" + d.placeOfSupplyCode() + ")")));
        if (taxed) h.append(row("Reverse charge", "No"));
        h.append("</table></header>");

        // Parties.
        h.append("<section class=\"parties\"><div><h2>Supplier</h2><p class=\"name\">").append(e(d.supplierName())).append("</p>");
        if (d.supplierTradeName() != null) h.append("<p>Trading as ").append(e(d.supplierTradeName())).append("</p>");
        if (d.supplierAddress() != null) h.append("<p>").append(e(d.supplierAddress())).append("</p>");
        if (d.supplierState() != null) h.append("<p>State: ").append(e(d.supplierState()))
                .append(d.supplierStateCode() == null ? "" : " (" + e(d.supplierStateCode()) + ")").append("</p>");
        h.append("<p>GSTIN: <b>").append(d.supplierGstin() == null ? "Not registered" : e(d.supplierGstin())).append("</b></p></div>");
        h.append("<div><h2>").append(GstDocument.COMMISSION_INVOICE.equals(d.docType()) ? "Billed to" : "Customer").append("</h2><p class=\"name\">")
                .append(e(d.recipientName() == null ? "Guest" : d.recipientName())).append("</p>");
        if (d.recipientAddress() != null) h.append("<p>").append(e(d.recipientAddress())).append("</p>");
        if (d.recipientGstin() != null) h.append("<p>GSTIN: <b>").append(e(d.recipientGstin())).append("</b></p>");
        else if (GstDocument.COMMISSION_INVOICE.equals(d.docType())) h.append("<p>GSTIN: Not registered</p>");
        if (d.recipientEmail() != null) h.append("<p>").append(e(d.recipientEmail())).append("</p>");
        if (d.recipientPhone() != null) h.append("<p>").append(e(d.recipientPhone())).append("</p>");
        h.append("</div></section>");

        // The line.
        h.append("<table class=\"items\"><thead><tr><th>Description</th><th>SAC</th><th class=\"r\">Qty</th>");
        if (n(d.discountCents()) > 0) h.append("<th class=\"r\">Amount</th><th class=\"r\">Discount</th>");
        h.append("<th class=\"r\">").append(taxed ? "Taxable value" : "Value").append("</th>");
        if (taxed && intra) h.append("<th class=\"r\">CGST ").append(e(half)).append("</th><th class=\"r\">SGST ").append(e(half)).append("</th>");
        if (taxed && !intra) h.append("<th class=\"r\">IGST ").append(e(full)).append("</th>");
        h.append("<th class=\"r\">Total</th></tr></thead><tbody><tr><td>").append(e(d.description())).append("</td><td>")
                .append(e(d.sacCode() == null ? "-" : d.sacCode())).append("</td><td class=\"r\">").append(d.quantity()).append("</td>");
        if (n(d.discountCents()) > 0) h.append(cell(d, d.grossCents())).append(cell(d, -n(d.discountCents())));
        h.append(cell(d, d.taxableCents()));
        if (taxed && intra) h.append(cell(d, d.cgstCents())).append(cell(d, d.sgstCents()));
        if (taxed && !intra) h.append(cell(d, d.igstCents()));
        h.append(cell(d, d.totalCents())).append("</tr></tbody></table>");

        // Totals.
        h.append("<table class=\"totals\">").append(row(taxed ? "Taxable value" : "Value of supply", money(d, d.taxableCents())));
        if (taxed && intra) {
            h.append(row("CGST @ " + half, money(d, d.cgstCents()))).append(row("SGST @ " + half, money(d, d.sgstCents())));
        }
        if (taxed && !intra) h.append(row("IGST @ " + full, money(d, d.igstCents())));
        h.append("<tr class=\"grand\"><th>").append(GstDocument.CREDIT_NOTE.equals(d.docType()) ? "Total credited" : "Total").append("</th><td>")
                .append(e(money(d, d.totalCents()))).append("</td></tr></table>");
        h.append("<p class=\"words\">").append(e(words(n(d.totalCents()), d.currency()))).append("</p>");

        if (d.commissionBaseCents() != null) {
            h.append("<p class=\"muted\">Commission at ").append(e(d.commissionPct().stripTrailingZeros().toPlainString()))
                    .append("% on online collections, net of refunds, of ").append(e(money(d, d.commissionBaseCents()))).append(".</p>");
        }
        if (d.notes() != null) h.append("<p class=\"muted\">").append(e(d.notes())).append("</p>");

        // Signature and who issued it.
        h.append("<footer><div class=\"muted\">");
        if (GstDocument.COMMISSION_INVOICE.equals(d.docType())) {
            h.append("Nirvana's invoice to the spa for its marketplace service.");
        } else {
            h.append("Issued by ").append(e(d.supplierName())).append(", the supplier of this service, through the Nirvana marketplace.");
        }
        h.append(" Computer-generated document.</div><div class=\"sign\">For ").append(e(d.supplierName()))
                .append("<br><br><span class=\"muted\">Authorised signatory</span></div></footer>");
        h.append("</main></body></html>");
        return h.toString();
    }

    private static final String CSS = """
            *{box-sizing:border-box}body{margin:0;background:#f3f4f6;font:13px/1.45 system-ui,-apple-system,'Segoe UI',Roboto,Arial,sans-serif;color:#1f2937}
            main{max-width:820px;margin:16px auto;background:#fff;padding:36px 40px;border:1px solid #e5e7eb}
            .bar{max-width:820px;margin:16px auto 0;text-align:right}.bar button{font:inherit;padding:6px 14px;border:1px solid #203523;background:#203523;color:#fff;border-radius:6px;cursor:pointer}
            header{display:flex;justify-content:space-between;gap:24px;border-bottom:2px solid #203523;padding-bottom:14px}
            h1{margin:0;font-size:22px;letter-spacing:.04em;text-transform:uppercase;color:#203523}h2{margin:0 0 6px;font-size:11px;text-transform:uppercase;letter-spacing:.08em;color:#64748b}
            p{margin:2px 0}.name{font-weight:600;font-size:14px}.muted{color:#64748b;font-size:12px}
            .meta th{text-align:left;font-weight:500;color:#64748b;padding:1px 12px 1px 0}.meta td{font-weight:600}
            .parties{display:grid;grid-template-columns:1fr 1fr;gap:24px;margin:18px 0}
            table{border-collapse:collapse}.items{width:100%;margin-top:6px}.items th{background:#203523;color:#fff;font-weight:600;font-size:11px;text-align:left;padding:7px 8px}
            .items td{padding:9px 8px;border-bottom:1px solid #e5e7eb;vertical-align:top}.r{text-align:right;white-space:nowrap}
            .totals{margin:12px 0 0 auto;min-width:300px}.totals th{text-align:left;font-weight:500;color:#475569;padding:3px 16px 3px 0}.totals td{text-align:right;padding:3px 0}
            .totals .grand th,.totals .grand td{border-top:2px solid #203523;font-weight:700;font-size:15px;color:#111827;padding-top:8px}
            .words{text-align:right;font-style:italic;color:#475569;margin-top:6px}
            footer{display:flex;justify-content:space-between;gap:24px;margin-top:40px;border-top:1px solid #e5e7eb;padding-top:14px}.sign{text-align:right;min-width:220px}
            @media print{body{background:#fff}.bar{display:none}main{margin:0;border:0;padding:0;max-width:none}@page{size:A4;margin:16mm}}
            """;

    private static String row(String label, String value) {
        return "<tr><th>" + e(label) + "</th><td>" + e(value) + "</td></tr>";
    }

    private static String cell(GstDocument d, Long cents) {
        return "<td class=\"r\">" + e(money(d, cents)) + "</td>";
    }

    static String money(GstDocument d, Long cents) {
        NumberFormat f = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        long v = n(cents);
        return (v < 0 ? "- " : "") + (d.currency() == null ? "INR" : d.currency()) + " " + f.format(Math.abs(v) / 100.0);
    }

    /** "Rupees Two Thousand Nine Hundred Fifty and Paise Fifty Only", in the Indian system (lakh, crore). */
    static String words(long cents, String currency) {
        if (currency != null && !"INR".equals(currency)) return "";
        long rupees = cents / 100, paise = cents % 100;
        String s = "Rupees " + (rupees == 0 ? "Zero" : indian(rupees));
        if (paise > 0) s += " and Paise " + below100((int) paise);
        return s + " Only";
    }

    private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

    private static String indian(long n) {
        StringBuilder s = new StringBuilder();
        long crore = n / 10_000_000; n %= 10_000_000;
        long lakh = n / 100_000; n %= 100_000;
        long thousand = n / 1000; n %= 1000;
        long hundred = n / 100; n %= 100;
        if (crore > 0) s.append(indian(crore)).append(" Crore ");
        if (lakh > 0) s.append(below100((int) lakh)).append(" Lakh ");
        if (thousand > 0) s.append(below100((int) thousand)).append(" Thousand ");
        if (hundred > 0) s.append(ONES[(int) hundred]).append(" Hundred ");
        if (n > 0) s.append(below100((int) n));
        return s.toString().trim();
    }

    private static String below100(int n) {
        if (n < 20) return ONES[n];
        return (TENS[n / 10] + (n % 10 == 0 ? "" : " " + ONES[n % 10])).trim();
    }

    private static long n(Long v) {
        return v == null ? 0 : v;
    }

    private static String e(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }
}
