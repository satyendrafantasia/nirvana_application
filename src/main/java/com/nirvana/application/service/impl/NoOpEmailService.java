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
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "notifications.sendgrid", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpEmailService implements EmailService {


    private final NotificationLogRepository notificationLogRepository;
    private final SendGridProperties sendGridProperties;
    private final JavaMailSender mailSender;

    @Override
    public void sendInvoiceEmail(InvoiceEmailDto dto) {
        String subject = (dto.documentTitle() == null ? "Invoice" : dto.documentTitle()) + " " + dto.invoiceNumber();
        NotificationLog logEntry = NotificationLog.builder()
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.QUEUED)
                .destination(dto.toEmail())
                .title(subject)
                .message("Invoice ready for booking " + dto.bookingReference())
                .build();

        try {
            MimeMessage message = buildMimeMessage(dto.toEmail(), dto.toName(), subject, InvoiceEmailContent.render(dto));
            mailSender.send(message);
            logEntry.setStatus(NotificationStatus.SENT);
            log.debug("Notification delivery processed");
        } catch (MessagingException | MailException e) {
            logEntry.setStatus(NotificationStatus.FAILED);
            logEntry.setErrorMessage("Email transport failed");
            log.error("Notification delivery failed");
        } finally {
            notificationLogRepository.save(logEntry);
        }
    }

    @Override
    public void sendCorporateEmployeeInvite(String employeeEmail) {
        String subject = "You're invited to Nirvana";
        String body = "<p>Your Nirvana corporate account is ready.</p>"
                + "<p>Use the email sign-in option with this email address to access your benefits.</p>";
        sendSimpleMail(employeeEmail, subject, body);
    }

    @Override
    public void sendCorporateBenefitsActivated(String employeeEmail, String dealName) {
        String subject = "Your corporate benefits are active";
        String body = "<p>Hello,</p>" +
                "<p>Your corporate benefits have been activated.</p>" +
                "<p>Deal: <strong>" + InvoiceEmailContent.text(Optional.ofNullable(dealName).orElse("N/A")) + "</strong></p>" +
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
            log.debug("Notification delivery processed");
        } catch (MessagingException | MailException e) {
            log.error("Notification delivery failed");
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

}
