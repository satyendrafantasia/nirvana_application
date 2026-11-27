package com.nirvana.application.service.impl;

import com.nirvana.application.config.SendGridProperties;
import com.nirvana.application.model.Booking;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.User;
import com.nirvana.application.model.WaitlistEntry;
import com.nirvana.application.model.Slot;
import com.nirvana.application.service.NotificationService;
import com.nirvana.application.service.impl.BookingEmailContent.Email;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "notifications.sendgrid", name = "enabled", havingValue = "true")
public class SendGridNotificationService implements NotificationService {

    private final SendGrid sendGrid;
    private final SendGridProperties properties;

    @Override
    public void sendRequestUpdate(String email, String subject, String html) {
        sendEmail(email, subject, html, "BOOKING_REQUEST");
    }

    @Override
    public void notifySpaOwnerBookingConfirmed(Booking booking) {
        toSpa(booking, BookingEmailContent.ownerConfirmed(booking, properties.getOwnerSubject()));
    }

    @Override
    public void notifyCustomerBookingConfirmed(Booking booking) {
        toCustomer(booking, BookingEmailContent.customerConfirmed(booking, properties.getCustomerSubject()));
    }

    @Override
    public void notifyCustomerBookingCancelled(Booking booking) {
        toCustomer(booking, BookingEmailContent.customerCancelled(booking));
    }

    @Override
    public void notifySpaOwnerBookingCancelled(Booking booking) {
        toSpa(booking, BookingEmailContent.ownerCancelled(booking));
    }

    @Override
    public void notifyCustomerBookingRescheduled(Booking booking) {
        toCustomer(booking, BookingEmailContent.customerRescheduled(booking));
    }

    @Override
    public void notifySpaOwnerBookingRescheduled(Booking booking) {
        toSpa(booking, BookingEmailContent.ownerRescheduled(booking));
    }

    @Override
    public void notifyCustomerBookingReminder(Booking booking) {
        toCustomer(booking, BookingEmailContent.customerReminder(booking));
    }

    @Override
    public void notifyWaitlistUserSlotAvailable(WaitlistEntry entry, Slot slot) {
        String email = entry.getContactEmail();
        if ((email == null || email.isBlank()) && entry.getUser() != null) {
            email = entry.getUser().getEmail();
        }
        if (email == null || email.isBlank()) {
            log.warn("Skipping waitlist notification for entry {} due to missing email", entry.getId());
            return;
        }
        String spaName = slot.getSpa() != null && slot.getSpa().getName() != null ? slot.getSpa().getName() : "the spa";
        String body = "<p>A time you were waiting for is available again.</p><ul>"
                + "<li><strong>Spa:</strong> " + HtmlUtils.htmlEscape(spaName) + "</li>"
                + "<li><strong>When:</strong> " + BookingEmailContent.formatStart(slot.getStartTs(), slot.getSpa()) + "</li>"
                + "</ul><p>It is not held for you. Book it soon if you still want it.</p>";
        sendEmail(email, "A time just opened up", body, "WAITLIST_SLOT_AVAILABLE");
    }

    private void toCustomer(Booking booking, Email email) {
        User user = booking.getUser();
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            if (!email.templateCode().equals("BOOKING_REMINDER"))
                throw new com.nirvana.application.service.BookingContactUnavailable("No email address");
            return;
        }
        sendEmail(user.getEmail(), email.subject(), email.html(), email.templateCode());
    }

    private void toSpa(Booking booking, Email email) {
        Spa spa = booking.getSpa();
        String to = spa != null && spa.getEmail() != null && !spa.getEmail().isBlank()
                ? spa.getEmail() : properties.getOwnerFallbackEmail();
        if (to == null || to.isBlank()) {
            if (!email.templateCode().equals("BOOKING_REMINDER"))
                throw new com.nirvana.application.service.BookingContactUnavailable("No email address");
            return;
        }
        sendEmail(to, email.subject(), email.html(), email.templateCode());
    }

    private void sendEmail(String toEmail, String subject, String htmlContent, String templateCode) {
        if (properties.getFromEmail() == null || properties.getFromEmail().isBlank()) {
            throw new IllegalStateException("Sender email is missing");
        }

        var from = new com.sendgrid.helpers.mail.objects.Email(properties.getFromEmail(), properties.getFromName());
        var to = new com.sendgrid.helpers.mail.objects.Email(toEmail);
        Mail mail = new Mail(from, subject, to, new Content("text/html", htmlContent));
        Request request = new Request();
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sendGrid.api(request);
            if (response.getStatusCode() >= 400) {
                throw new IllegalStateException("Email delivery failed with status " + response.getStatusCode());
            } else {
                log.debug("{} notification delivery processed", templateCode);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Email delivery failed", e);
        }
    }
}
