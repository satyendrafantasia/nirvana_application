package com.nirvana.application.service.impl;

import com.nirvana.application.config.SendGridProperties;
import com.nirvana.application.model.Booking;
import com.nirvana.application.model.NotificationLog;
import com.nirvana.application.model.Slot;
import com.nirvana.application.model.User;
import com.nirvana.application.model.WaitlistEntry;
import com.nirvana.application.model.enums.NotificationChannel;
import com.nirvana.application.model.enums.NotificationStatus;
import com.nirvana.application.repository.NotificationLogRepository;
import com.nirvana.application.service.NotificationService;
import com.nirvana.application.service.impl.BookingEmailContent.Email;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Default delivery channel: sends booking emails through the configured SMTP server
 * (spring.mail.*) and records every attempt in notification_log. Used whenever SendGrid is off.
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "notifications.sendgrid", name = "enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class NoOpNotificationService implements NotificationService {

    private final NotificationLogRepository notificationLogRepository;
    private final JavaMailSender mailSender;
    private final SendGridProperties properties;

    @Override
    public void sendRequestUpdate(String email, String subject, String html) {
        deliver(null, email, new Email("BOOKING_REQUEST", subject, html), "Appointment request update");
    }

    @Override
    public void notifySpaOwnerBookingConfirmed(Booking booking) {
        send(booking, null, spaDestination(booking), BookingEmailContent.ownerConfirmed(booking, properties.getOwnerSubject()));
    }

    @Override
    public void notifyCustomerBookingConfirmed(Booking booking) {
        send(booking, booking.getUser(), customerDestination(booking), BookingEmailContent.customerConfirmed(booking, properties.getCustomerSubject()));
    }

    @Override
    public void notifyCustomerBookingCancelled(Booking booking) {
        send(booking, booking.getUser(), customerDestination(booking), BookingEmailContent.customerCancelled(booking));
    }

    @Override
    public void notifySpaOwnerBookingCancelled(Booking booking) {
        send(booking, null, spaDestination(booking), BookingEmailContent.ownerCancelled(booking));
    }

    @Override
    public void notifyCustomerBookingRescheduled(Booking booking) {
        send(booking, booking.getUser(), customerDestination(booking), BookingEmailContent.customerRescheduled(booking));
    }

    @Override
    public void notifySpaOwnerBookingRescheduled(Booking booking) {
        send(booking, null, spaDestination(booking), BookingEmailContent.ownerRescheduled(booking));
    }

    @Override
    public void notifyCustomerBookingReminder(Booking booking) {
        send(booking, booking.getUser(), customerDestination(booking), BookingEmailContent.customerReminder(booking));
    }

    @Override
    public void notifyWaitlistUserSlotAvailable(WaitlistEntry entry, Slot slot) {
        String email = entry.getContactEmail();
        if ((email == null || email.isBlank()) && entry.getUser() != null) {
            email = entry.getUser().getEmail();
        }
        String spaName = slot.getSpa() != null && slot.getSpa().getName() != null ? slot.getSpa().getName() : "the spa";
        String html = "<p>A time you were waiting for is available again.</p><ul>"
                + "<li><strong>Spa:</strong> " + HtmlUtils.htmlEscape(spaName) + "</li>"
                + "<li><strong>When:</strong> " + BookingEmailContent.formatStart(slot.getStartTs(), slot.getSpa()) + "</li>"
                + "</ul><p>It is not held for you. Book it soon if you still want it.</p>";
        deliver(entry.getUser(), email, new Email("WAITLIST_SLOT_AVAILABLE", "A time just opened up", html),
                "Waitlist entry " + entry.getId());
    }

    private void send(Booking booking, User recipient, String destination, Email email) {
        deliver(recipient, destination, email, "Booking ref " + booking.getBookingReference());
    }

    private void deliver(User recipient, String destination, Email email, String context) {
        if (destination == null || destination.isBlank()) {
            if (email.templateCode().startsWith("BOOKING_") && !email.templateCode().equals("BOOKING_REMINDER"))
                throw new com.nirvana.application.service.BookingContactUnavailable("No email address");
            log.warn("Skipping {} notification: no destination ({})", email.templateCode(), context);
            return;
        }

        NotificationLog logEntry = NotificationLog.builder()
                .user(recipient)
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.QUEUED)
                .destination(destination)
                .templateCode(email.templateCode())
                .title(email.subject())
                .message(context)
                .build();

        try {
            mailSender.send(buildMimeMessage(destination, email.subject(), email.html()));
            logEntry.setStatus(NotificationStatus.SENT);
            logEntry.setSentAt(OffsetDateTime.now(ZoneOffset.UTC));
        } catch (MessagingException | MailException e) {
            logEntry.setStatus(NotificationStatus.FAILED);
            String reason = e.getMessage();
            logEntry.setErrorMessage(reason != null && reason.length() > 1000 ? reason.substring(0, 1000) : reason);
            log.error("{} notification delivery failed ({})", email.templateCode(), context);
            if (email.templateCode().startsWith("BOOKING_") && !email.templateCode().equals("BOOKING_REMINDER"))
                throw new IllegalStateException("Booking email delivery failed", e);
        } finally {
            notificationLogRepository.save(logEntry);
        }
    }

    private String spaDestination(Booking booking) {
        if (booking.getSpa() != null) {
            if (booking.getSpa().getEmail() != null && !booking.getSpa().getEmail().isBlank()) {
                return booking.getSpa().getEmail();
            }
            if (booking.getSpa().getSpaManager() != null && booking.getSpa().getSpaManager().getUser() != null) {
                return booking.getSpa().getSpaManager().getUser().getEmail();
            }
        }
        return properties.getOwnerFallbackEmail();
    }

    private String customerDestination(Booking booking) {
        return booking.getUser() != null ? booking.getUser().getEmail() : null;
    }

    private MimeMessage buildMimeMessage(String toEmail, String subject, String html) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
        if (properties.getFromEmail() != null && !properties.getFromEmail().isBlank()) {
            helper.setFrom(properties.getFromEmail());
        }
        helper.setTo(new InternetAddress(toEmail));
        helper.setSubject(subject);
        helper.setText(html, true);
        return message;
    }
}
