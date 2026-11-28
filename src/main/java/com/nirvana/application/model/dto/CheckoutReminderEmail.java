package com.nirvana.application.model.dto;

/**
 * A reminder about a checkout the guest left unpaid. {@code oneClickUnsubscribeUrl} is the API
 * address for the List-Unsubscribe header (RFC 8058); {@code unsubscribeUrl} is the page the
 * footer links to.
 */
public record CheckoutReminderEmail(
        String toEmail,
        String toName,
        String spaName,
        String serviceName,
        String whenText,
        String resumeUrl,
        String unsubscribeUrl,
        String oneClickUnsubscribeUrl,
        String bookingReference
) {}
