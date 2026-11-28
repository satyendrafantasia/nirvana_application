package com.nirvana.application.service.impl;

import com.nirvana.application.model.dto.CheckoutReminderEmail;

/** The short, friendly reminder for a checkout the guest left before paying. */
public final class CheckoutReminderEmailContent {
    private CheckoutReminderEmailContent() {}

    public static String subject(CheckoutReminderEmail dto) {
        return "Your " + orDefault(dto.serviceName(), "spa appointment") + " is still waiting";
    }

    public static String render(CheckoutReminderEmail dto) {
        String name = dto.toName() == null || dto.toName().isBlank() ? "there" : dto.toName().strip();
        String treatment = orDefault(dto.serviceName(), "your treatment");
        String spa = orDefault(dto.spaName(), "the spa");
        return "<div style=\"font-family:Arial,sans-serif;max-width:520px;color:#1e293b\">"
                + "<p>Hi " + e(name) + ",</p>"
                + "<p>You were booking <strong>" + e(treatment) + "</strong> at <strong>" + e(spa) + "</strong>"
                + (dto.whenText() == null ? "" : " for " + e(dto.whenText()))
                + ", but the payment didn't go through, so nothing has been booked yet.</p>"
                + "<p>If you'd still like to go, pick up where you left off. We'll show the same time if it's"
                + " still free, or the nearest times if it isn't.</p>"
                + "<p><a href=\"" + e(dto.resumeUrl()) + "\" style=\"display:inline-block;background:#0f766e;color:#ffffff;"
                + "padding:10px 18px;border-radius:8px;text-decoration:none\">Finish booking</a></p>"
                + "<p>No longer interested? No problem, you don't need to do anything.</p>"
                + "<p style=\"font-size:12px;color:#64748b\">We send at most one reminder for a checkout."
                + " <a href=\"" + e(dto.unsubscribeUrl()) + "\" style=\"color:#64748b\">Stop these reminders</a></p>"
                + "</div>";
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static String e(String raw) {
        return InvoiceEmailContent.text(raw);
    }
}
