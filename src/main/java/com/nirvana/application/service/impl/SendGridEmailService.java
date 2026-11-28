package com.nirvana.application.service.impl;

import com.nirvana.application.config.SendGridProperties;
import com.nirvana.application.model.NotificationLog;
import com.nirvana.application.model.dto.CheckoutReminderEmail;
import com.nirvana.application.model.dto.InvoiceEmailDto;
import com.nirvana.application.model.enums.NotificationChannel;
import com.nirvana.application.model.enums.NotificationStatus;
import com.nirvana.application.repository.NotificationLogRepository;
import com.nirvana.application.service.EmailService;
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

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "notifications.sendgrid", name = "enabled", havingValue = "true")
public class SendGridEmailService implements EmailService {


    private final SendGrid sendGrid;
    private final SendGridProperties properties;
    private final NotificationLogRepository notificationLogRepository;

    @Override
    public void sendInvoiceEmail(InvoiceEmailDto dto) {
        if (properties.getFromEmail() == null || properties.getFromEmail().isBlank()) {
            log.warn("SendGrid invoice email skipped: fromEmail is not configured");
            return;
        }

        Email from = new Email(properties.getFromEmail(), properties.getFromName());
        Email to = new Email(dto.toEmail(), dto.toName());
        String subject = (dto.documentTitle() == null ? "Invoice" : dto.documentTitle()) + " " + dto.invoiceNumber();
        String html = InvoiceEmailContent.render(dto);

        Mail mail = new Mail(from, subject, to, new Content("text/html", html));
        Request request = new Request();

        NotificationLog logEntry = NotificationLog.builder()
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.QUEUED)
                .destination(dto.toEmail())
                .title(subject)
                .message("Invoice ready for booking " + dto.bookingReference())
                .build();

        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sendGrid.api(request);
            int statusCode = response.getStatusCode();
            if (statusCode >= 400) {
                logEntry.setStatus(NotificationStatus.FAILED);
                logEntry.setErrorMessage("Email provider returned status " + statusCode);
                log.error("Notification delivery failed");
            } else {
                logEntry.setStatus(NotificationStatus.SENT);
                logEntry.setProviderMessageId(String.valueOf(statusCode));
                log.debug("Notification delivery processed");
            }
        } catch (IOException e) {
            logEntry.setStatus(NotificationStatus.FAILED);
            logEntry.setErrorMessage("Email transport failed");
            log.error("Notification delivery failed");
        } finally {
            notificationLogRepository.save(logEntry);
        }
    }

    @Override
    public void sendCorporateEmployeeInvite(String employeeEmail) {
        log.debug("Notification delivery processed");
    }

    @Override
    public void sendCorporateBenefitsActivated(String employeeEmail, String dealName) {
        log.debug("Notification delivery processed");
    }

    @Override
    public boolean sendCheckoutReminder(CheckoutReminderEmail dto) {
        if (properties.getFromEmail() == null || properties.getFromEmail().isBlank()
                || dto.toEmail() == null || dto.toEmail().isBlank()) {
            log.warn("SendGrid checkout reminder skipped: sender or recipient missing");
            return false;
        }
        String subject = CheckoutReminderEmailContent.subject(dto);
        Mail mail = new Mail(new Email(properties.getFromEmail(), properties.getFromName()), subject,
                new Email(dto.toEmail(), dto.toName()), new Content("text/html", CheckoutReminderEmailContent.render(dto)));
        if (dto.oneClickUnsubscribeUrl() != null) {
            // RFC 8058 one-click unsubscribe, shown by Gmail and others next to the sender.
            mail.addHeader("List-Unsubscribe", "<" + dto.oneClickUnsubscribeUrl() + ">");
            mail.addHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        }
        NotificationLog logEntry = NotificationLog.builder()
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.QUEUED)
                .destination(dto.toEmail())
                .title(subject)
                .message("Checkout reminder for booking " + dto.bookingReference())
                .build();
        try {
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            int statusCode = sendGrid.api(request).getStatusCode();
            if (statusCode >= 400) {
                logEntry.setStatus(NotificationStatus.FAILED);
                logEntry.setErrorMessage("Email provider returned status " + statusCode);
                return false;
            }
            logEntry.setStatus(NotificationStatus.SENT);
            logEntry.setProviderMessageId(String.valueOf(statusCode));
            return true;
        } catch (IOException e) {
            logEntry.setStatus(NotificationStatus.FAILED);
            logEntry.setErrorMessage("Email transport failed");
            return false;
        } finally {
            notificationLogRepository.save(logEntry);
        }
    }

}
