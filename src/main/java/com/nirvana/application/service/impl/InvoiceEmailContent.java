package com.nirvana.application.service.impl;

import com.nirvana.application.model.dto.InvoiceEmailDto;
import org.springframework.web.util.HtmlUtils;
import java.net.URI;

final class InvoiceEmailContent {
    private InvoiceEmailContent() {}

    static String text(String raw) {
        return raw == null ? "" : HtmlUtils.htmlEscape(raw, "UTF-8");
    }

    private static boolean safeWebLink(String raw) {
        if (raw == null) return false;
        try {
            URI uri = URI.create(raw);
            return uri.getHost() != null && uri.getUserInfo() == null
                    && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    static String render(InvoiceEmailDto dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p>Hello ").append(text(dto.toName() != null ? dto.toName() : "there")).append(",</p>");
        if (dto.documentTitle() == null) sb.append("<p>Your Nirvana invoice is ready.</p>");
        else com.nirvana.application.service.gst.GstMail.intro(sb, dto);
        sb.append("<ul>");
        sb.append("<li><strong>").append(text(dto.documentTitle() == null ? "Invoice" : dto.documentTitle())).append(" #</strong>: ").append(text(dto.invoiceNumber())).append("</li>");
        sb.append("<li><strong>Booking Reference</strong>: ").append(text(dto.bookingReference())).append("</li>");
        if (dto.bookingStartTs() != null) {
            sb.append("<li><strong>Scheduled</strong>: ").append(text(BookingEmailContent.formatStart(dto.bookingStartTs(), dto.spaTimezone()))).append("</li>");
        }
        sb.append("<li><strong>Spa</strong>: ").append(text(dto.spaName())).append("</li>");
        sb.append("<li><strong>Service</strong>: ").append(text(dto.serviceName())).append("</li>");
        if (dto.documentTitle() == null) {
            sb.append("<li><strong>Total</strong>: ").append(text(dto.currency())).append(" ").append(dto.totalCents() / 100.0).append("</li>");
        } else {
            com.nirvana.application.service.gst.GstMail.amounts(sb, dto);
        }
        sb.append("</ul>");
        if (safeWebLink(dto.invoicePdfUrl())) {
            sb.append("<p>You can download your invoice here: <a href=\"")
                    .append(text(dto.invoicePdfUrl())).append("\">Invoice PDF</a></p>");
        }

        if (dto.spaAddressLine1() != null) {
            sb.append("<p><strong>Spa address</strong>: ")
                    .append(text(dto.spaAddressLine1()));
            if (dto.spaAddressLine2() != null) sb.append(", ").append(text(dto.spaAddressLine2()));
            if (dto.spaCity() != null) sb.append(", ").append(text(dto.spaCity()));
            if (dto.spaState() != null) sb.append(", ").append(text(dto.spaState()));
            if (dto.spaPostalCode() != null) sb.append(" - ").append(text(dto.spaPostalCode()));
            if (dto.spaCountry() != null) sb.append(", ").append(text(dto.spaCountry()));
            sb.append("</p>");
        }
        return sb.toString();
    }
}
