package com.nirvana.application.service.impl;

import com.nirvana.application.config.SendGridProperties;
import com.nirvana.application.model.NotificationLog;
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
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "notifications.sendgrid", name = "enabled", havingValue = "true")
public class SendGridEmailService implements EmailService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm z");

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
        Email to = new Email(dto.getToEmail(), dto.getToName());
        String subject = "Invoice " + dto.getInvoiceNumber();
        String html = buildInvoiceBody(dto);

        Mail mail = new Mail(from, subject, to, new Content("text/html", html));
        Request request = new Request();

        NotificationLog logEntry = NotificationLog.builder()
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.QUEUED)
                .destination(dto.getToEmail())
                .title(subject)
                .message("Invoice ready for booking " + dto.getBookingReference())
                .build();

        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sendGrid.api(request);
            int statusCode = response.getStatusCode();
            if (statusCode >= 400) {
                logEntry.setStatus(NotificationStatus.FAILED);
                logEntry.setErrorMessage(response.getBody());
                log.error("Failed to send invoice email {} to {} status {}", dto.getInvoiceNumber(), dto.getToEmail(), statusCode);
            } else {
                logEntry.setStatus(NotificationStatus.SENT);
                logEntry.setProviderMessageId(String.valueOf(statusCode));
                log.info("Invoice email {} sent to {} with status {}", dto.getInvoiceNumber(), dto.getToEmail(), statusCode);
            }
        } catch (IOException e) {
            logEntry.setStatus(NotificationStatus.FAILED);
            logEntry.setErrorMessage(e.getMessage());
            log.error("Error sending invoice email {} to {}", dto.getInvoiceNumber(), dto.getToEmail(), e);
        } finally {
            notificationLogRepository.save(logEntry);
        }
    }

    private String buildInvoiceBody(InvoiceEmailDto dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p>Hello ").append(dto.getToName() != null ? dto.getToName() : "there").append(",</p>");
        sb.append("<p>Your Nirvana invoice is ready.</p>");
        sb.append("<ul>");
        sb.append("<li><strong>Invoice #</strong>: ").append(dto.getInvoiceNumber()).append("</li>");
        sb.append("<li><strong>Booking Reference</strong>: ").append(dto.getBookingReference()).append("</li>");
        if (dto.getBookingStartTs() != null) {
            sb.append("<li><strong>Scheduled</strong>: ").append(DATE_TIME_FORMATTER.format(dto.getBookingStartTs())).append("</li>");
        }
        sb.append("<li><strong>Spa</strong>: ").append(dto.getSpaName()).append("</li>");
        sb.append("<li><strong>Service</strong>: ").append(dto.getServiceName()).append("</li>");
        sb.append("<li><strong>Total</strong>: ").append(dto.getCurrency()).append(" ").append(dto.getTotalCents() / 100.0).append("</li>");
        sb.append("</ul>");
        sb.append("<p>You can download your invoice here: <a href=\"")
                .append(dto.getInvoicePdfUrl())
                .append("\">Invoice PDF</a></p>");

        if (dto.getSpaAddressLine1() != null) {
            sb.append("<p><strong>Spa address</strong>: ")
                    .append(dto.getSpaAddressLine1());
            if (dto.getSpaAddressLine2() != null) sb.append(", ").append(dto.getSpaAddressLine2());
            if (dto.getSpaCity() != null) sb.append(", ").append(dto.getSpaCity());
            if (dto.getSpaState() != null) sb.append(", ").append(dto.getSpaState());
            if (dto.getSpaPostalCode() != null) sb.append(" - ").append(dto.getSpaPostalCode());
            if (dto.getSpaCountry() != null) sb.append(", ").append(dto.getSpaCountry());
            sb.append("</p>");
        }
        return sb.toString();
    }
}
