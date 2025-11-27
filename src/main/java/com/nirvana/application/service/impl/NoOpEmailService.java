// src/main/java/com/nirvana/application/service/impl/NoOpEmailService.java
package com.nirvana.application.service.impl;

import com.nirvana.application.config.SendGridProperties;
import com.nirvana.application.model.NotificationLog;
import com.nirvana.application.model.dto.InvoiceEmailDto;
import com.nirvana.application.model.enums.NotificationChannel;
import com.nirvana.application.model.enums.NotificationStatus;
import com.nirvana.application.repository.NotificationLogRepository;
import com.nirvana.application.service.EmailService;
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
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "notifications.sendgrid", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpEmailService implements EmailService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm z");

    private final NotificationLogRepository notificationLogRepository;
    private final SendGridProperties sendGridProperties;
    private final JavaMailSender mailSender;

    @Override
    public void sendInvoiceEmail(InvoiceEmailDto dto) {
        String subject = "Invoice " + dto.invoiceNumber();
        NotificationLog logEntry = NotificationLog.builder()
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.QUEUED)
                .destination(dto.toEmail())
                .title(subject)
                .message("Invoice ready for booking " + dto.bookingReference())
                .build();

        try {
            MimeMessage message = buildMimeMessage(dto.toEmail(), dto.toName(), subject, buildInvoiceBody(dto));
            mailSender.send(message);
            logEntry.setStatus(NotificationStatus.SENT);
            log.info("Invoice email {} sent to {} (smtp fallback)", dto.invoiceNumber(), dto.toEmail());
        } catch (MessagingException | MailException e) {
            logEntry.setStatus(NotificationStatus.FAILED);
            logEntry.setErrorMessage(e.getMessage());
            log.error("Failed to send invoice email {} to {}", dto.invoiceNumber(), dto.toEmail(), e);
        } finally {
            notificationLogRepository.save(logEntry);
        }
    }

    @Override
    public void sendCorporateEmployeeInvite(String employeeEmail, String username, String tempPassword) {
        String subject = "You're invited to Nirvana";
        String body = "<p>Hello,</p>" +
                "<p>Your corporate account has been created.</p>" +
                "<ul>" +
                "<li><strong>Username:</strong> " + username + "</li>" +
                "<li><strong>Temporary password:</strong> " + tempPassword + "</li>" +
                "</ul>" +
                "<p>Please log in and update your password.</p>";
        sendSimpleMail(employeeEmail, subject, body);
    }

    @Override
    public void sendCorporateBenefitsActivated(String employeeEmail, String dealName) {
        String subject = "Your corporate benefits are active";
        String body = "<p>Hello,</p>" +
                "<p>Your corporate benefits have been activated.</p>" +
                "<p>Deal: <strong>" + Optional.ofNullable(dealName).orElse("N/A") + "</strong></p>" +
                "<p>Open the app to explore available offers.</p>";
        sendSimpleMail(employeeEmail, subject, body);
    }

    private void sendSimpleMail(String toEmail, String subject, String html) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Skipping email send: destination missing for subject {}", subject);
            return;
        }

        try {
            MimeMessage message = buildMimeMessage(toEmail, null, subject, html);
            mailSender.send(message);
            log.info("Sent email to {} with subject {} (smtp fallback)", toEmail, subject);
        } catch (MessagingException | MailException e) {
            log.error("Failed to send email to {} with subject {}", toEmail, subject, e);
        }
    }

    private MimeMessage buildMimeMessage(String toEmail, String toName, String subject, String html) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
        if (sendGridProperties.getFromEmail() != null && !sendGridProperties.getFromEmail().isBlank()) {
            helper.setFrom(sendGridProperties.getFromEmail());
        }
        helper.setTo(new InternetAddress(toEmail));
        helper.setSubject(subject);
        helper.setText(html, true);
        return message;
    }

    private String buildInvoiceBody(InvoiceEmailDto dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p>Hello ").append(dto.toName() != null ? dto.toName() : "there").append(",</p>");
        sb.append("<p>Your Nirvana invoice is ready.</p>");
        sb.append("<ul>");
        sb.append("<li><strong>Invoice #</strong>: ").append(dto.invoiceNumber()).append("</li>");
        sb.append("<li><strong>Booking Reference</strong>: ").append(dto.bookingReference()).append("</li>");
        if (dto.bookingStartTs() != null) {
            sb.append("<li><strong>Scheduled</strong>: ").append(DATE_TIME_FORMATTER.format(dto.bookingStartTs())).append("</li>");
        }
        sb.append("<li><strong>Spa</strong>: ").append(dto.spaName()).append("</li>");
        sb.append("<li><strong>Service</strong>: ").append(dto.serviceName()).append("</li>");
        sb.append("<li><strong>Total</strong>: ").append(dto.currency()).append(" ").append(dto.totalCents() / 100.0).append("</li>");
        sb.append("</ul>");
        sb.append("<p>You can download your invoice here: <a href=\"")
                .append(dto.invoicePdfUrl())
                .append("\">Invoice PDF</a></p>");

        if (dto.spaAddressLine1() != null) {
            sb.append("<p><strong>Spa address</strong>: ")
                    .append(dto.spaAddressLine1());
            if (dto.spaAddressLine2() != null) sb.append(", ").append(dto.spaAddressLine2());
            if (dto.spaCity() != null) sb.append(", ").append(dto.spaCity());
            if (dto.spaState() != null) sb.append(", ").append(dto.spaState());
            if (dto.spaPostalCode() != null) sb.append(" - ").append(dto.spaPostalCode());
            if (dto.spaCountry() != null) sb.append(", ").append(dto.spaCountry());
            sb.append("</p>");
        }
        return sb.toString();
    }
}
