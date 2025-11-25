package com.nirvana.application.service.impl;

import com.nirvana.application.config.SendGridProperties;
import com.nirvana.application.model.Booking;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.User;
import com.nirvana.application.service.NotificationService;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "notifications.sendgrid", name = "enabled", havingValue = "true")
public class SendGridNotificationService implements NotificationService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm z");

    private final SendGrid sendGrid;
    private final SendGridProperties properties;

    @Override
    public void notifySpaOwnerBookingConfirmed(Booking booking) {
        String toEmail = resolveSpaOwnerEmail(booking);
        if (toEmail == null) {
            log.warn("Skipping spa owner notification: no email for booking ref {}", booking.getBookingReference());
            return;
        }

        String subject = properties.getOwnerSubject() + " - " + booking.getBookingReference();
        String body = buildOwnerBody(booking);
        sendEmail(toEmail, subject, body);
    }

    @Override
    public void notifyCustomerBookingConfirmed(Booking booking) {
        User user = booking.getUser();
        if (user == null || user.getEmail() == null) {
            log.warn("Skipping customer notification: user email missing for booking ref {}", booking.getBookingReference());
            return;
        }

        String subject = properties.getCustomerSubject() + " - " + booking.getBookingReference();
        String body = buildCustomerBody(booking);
        sendEmail(user.getEmail(), subject, body);
    }

    private void sendEmail(String toEmail, String subject, String htmlContent) {
        if (properties.getFromEmail() == null || properties.getFromEmail().isBlank()) {
            log.warn("Cannot send email: fromEmail not configured");
            return;
        }

        Email from = new Email(properties.getFromEmail(), properties.getFromName());
        Email to = new Email(toEmail);
        Content content = new Content("text/html", htmlContent);

        Mail mail = new Mail(from, subject, to, content);
        Request request = new Request();
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sendGrid.api(request);
            int statusCode = response.getStatusCode();
            if (statusCode >= 400) {
                log.error("Failed to send email to {} with status {}: {}", toEmail, statusCode, response.getBody());
            } else {
                log.info("Notification email sent to {} with status {}", toEmail, statusCode);
            }
        } catch (IOException e) {
            log.error("Error sending email to {}", toEmail, e);
        }
    }

    private String resolveSpaOwnerEmail(Booking booking) {
        Spa spa = booking.getSpa();
        if (spa != null && spa.getEmail() != null && !spa.getEmail().isBlank()) {
            return spa.getEmail();
        }
        return properties.getOwnerFallbackEmail();
    }

    private String buildOwnerBody(Booking booking) {
        User user = booking.getUser();
        Spa spa = booking.getSpa();
        return "<p>Hello " + safe(spa != null ? spa.getOwnerName() : null) + ",</p>"
                + "<p>A new booking has been confirmed.</p>"
                + "<ul>"
                + "<li><strong>Reference:</strong> " + booking.getBookingReference() + "</li>"
                + "<li><strong>Guest:</strong> " + safe(user != null ? user.getName() : null) + " (" + safe(user != null ? user.getEmail() : null) + ")" + "</li>"
                + "<li><strong>Service:</strong> " + safe(booking.getService() != null ? booking.getService().getName() : null) + "</li>"
                + "<li><strong>Scheduled:</strong> " + formatStartTime(booking) + "</li>"
                + "</ul>"
                + "<p>Please prepare to welcome the guest.</p>";
    }

    private String buildCustomerBody(Booking booking) {
        Spa spa = booking.getSpa();
        return "<p>Hello " + safe(booking.getUser() != null ? booking.getUser().getName() : null) + ",</p>"
                + "<p>Your booking is confirmed.</p>"
                + "<ul>"
                + "<li><strong>Reference:</strong> " + booking.getBookingReference() + "</li>"
                + "<li><strong>Spa:</strong> " + safe(spa != null ? spa.getName() : null) + "</li>"
                + "<li><strong>Service:</strong> " + safe(booking.getService() != null ? booking.getService().getName() : null) + "</li>"
                + "<li><strong>Scheduled:</strong> " + formatStartTime(booking) + "</li>"
                + "</ul>"
                + "<p>We look forward to seeing you!</p>";
    }

    private String formatStartTime(Booking booking) {
        OffsetDateTime start = booking.getStartTs();
        if (start == null) {
            return "N/A";
        }

        Spa spa = booking.getSpa();
        if (spa != null && spa.getTimezone() != null) {
            try {
                return start.atZoneSameInstant(ZoneId.of(spa.getTimezone())).format(DATE_TIME_FORMATTER);
            } catch (Exception e) {
                log.warn("Unable to format start time with timezone {}: {}", spa.getTimezone(), e.getMessage());
            }
        }
        return start.format(DATE_TIME_FORMATTER);
    }

    private String safe(String value) {
        return Objects.toString(value, "N/A");
    }
}
