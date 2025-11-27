package com.nirvana.application.service.impl;

import com.nirvana.application.config.SendGridProperties;
import com.nirvana.application.model.Booking;
import com.nirvana.application.model.NotificationLog;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.enums.NotificationChannel;
import com.nirvana.application.model.enums.NotificationStatus;
import com.nirvana.application.repository.NotificationLogRepository;
import com.nirvana.application.service.NotificationService;
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

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
@ConditionalOnProperty(prefix = "notifications.sendgrid", name = "enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class NoOpNotificationService implements NotificationService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm z");

    private final NotificationLogRepository notificationLogRepository;
    private final JavaMailSender mailSender;
    private final SendGridProperties properties;

    @Override
    public void notifySpaOwnerBookingConfirmed(Booking booking) {
        String toEmail = resolveSpaOwnerDestination(booking);
        String subject = properties.getOwnerSubject() + " - " + booking.getBookingReference();
        String body = buildOwnerBody(booking);
        sendEmailAndLog(booking, toEmail, subject, body);
    }

    @Override
    public void notifyCustomerBookingConfirmed(Booking booking) {
        String toEmail = resolveCustomerDestination(booking);
        String subject = properties.getCustomerSubject() + " - " + booking.getBookingReference();
        String body = buildCustomerBody(booking);
        sendEmailAndLog(booking, toEmail, subject, body);
    }

    @Override
    public void notifyCustomerBookingCancelled(Booking booking) {
        String toEmail = resolveCustomerDestination(booking);
        String subject = "Your booking was cancelled - " + booking.getBookingReference();
        String body = buildCancellationBody(booking, false);
        sendEmailAndLog(booking, toEmail, subject, body);
    }

    @Override
    public void notifySpaOwnerBookingCancelled(Booking booking) {
        String toEmail = resolveSpaOwnerDestination(booking);
        String subject = "Booking cancelled - " + booking.getBookingReference();
        String body = buildCancellationBody(booking, true);
        sendEmailAndLog(booking, toEmail, subject, body);
    }

    private void sendEmailAndLog(Booking booking, String destination, String subject, String body) {
        if (destination == null || destination.isBlank()) {
            log.warn("Skipping notification send: destination missing for booking ref {}", booking.getBookingReference());
            return;
        }

        NotificationLog logEntry = NotificationLog.builder()
                .user(booking.getUser())
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.QUEUED)
                .destination(destination)
                .templateCode("BOOKING_EVENT")
                .title(subject)
                .message("Booking ref " + booking.getBookingReference())
                .sentAt(booking.getUpdatedAt())
                .build();

        try {
            MimeMessage message = buildMimeMessage(destination, subject, body);
            mailSender.send(message);
            logEntry.setStatus(NotificationStatus.SENT);
            log.info("Notification email sent for booking {} -> {}", booking.getBookingReference(), destination);
        } catch (MessagingException | MailException e) {
            logEntry.setStatus(NotificationStatus.FAILED);
            logEntry.setErrorMessage(e.getMessage());
            log.error("Failed to send notification for booking {} to {}", booking.getBookingReference(), destination, e);
        } finally {
            notificationLogRepository.save(logEntry);
        }
    }

    private String resolveSpaOwnerDestination(Booking booking) {
        if (booking.getSpa() != null) {
            if (booking.getSpa().getEmail() != null) {
                return booking.getSpa().getEmail();
            }
            if (booking.getSpa().getSpaManager() != null && booking.getSpa().getSpaManager().getUser() != null) {
                return booking.getSpa().getSpaManager().getUser().getEmail();
            }
        }
        return properties.getOwnerFallbackEmail();
    }

    private String resolveCustomerDestination(Booking booking) {
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

    private String buildOwnerBody(Booking booking) {
        return "<p>Hello,</p>"
                + "<p>A new booking has been confirmed.</p>"
                + buildBookingSummary(booking)
                + "<p>Please prepare to welcome the guest.</p>";
    }

    private String buildCustomerBody(Booking booking) {
        return "<p>Hello " + safe(booking.getUser() != null ? booking.getUser().getName() : null) + ",</p>"
                + "<p>Your booking is confirmed.</p>"
                + buildBookingSummary(booking)
                + "<p>We look forward to seeing you!</p>";
    }

    private String buildCancellationBody(Booking booking, boolean forOwner) {
        String intro = forOwner ? "<p>A booking was cancelled.</p>" : "<p>Your booking was cancelled.</p>";
        return intro + buildBookingSummary(booking);
    }

    private String buildBookingSummary(Booking booking) {
        Spa spa = booking.getSpa();
        return "<ul>"
                + "<li><strong>Reference:</strong> " + booking.getBookingReference() + "</li>"
                + "<li><strong>Spa:</strong> " + safe(spa != null ? spa.getName() : null) + "</li>"
                + "<li><strong>Service:</strong> " + safe(booking.getService() != null ? booking.getService().getName() : null) + "</li>"
                + "<li><strong>Scheduled:</strong> " + formatStartTime(booking.getStartTs(), spa) + "</li>"
                + "</ul>";
    }

    private String formatStartTime(OffsetDateTime start, Spa spa) {
        if (start == null) {
            return "N/A";
        }
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
        return value != null && !value.isBlank() ? value : "N/A";
    }
}
