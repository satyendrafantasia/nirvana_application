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
        String subject = "Invoice " + dto.invoiceNumber();
        String html = buildInvoiceBody(dto);

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
                logEntry.setErrorMessage(response.getBody());
                log.error("Notification delivery failed");
            } else {
                logEntry.setStatus(NotificationStatus.SENT);
                logEntry.setProviderMessageId(String.valueOf(statusCode));
                log.debug("Notification delivery processed");
            }
        } catch (IOException e) {
            logEntry.setStatus(NotificationStatus.FAILED);
            logEntry.setErrorMessage(e.getMessage());
            log.error("Notification delivery failed");
        } finally {
            notificationLogRepository.save(logEntry);
        }
    }

    @Override
    public void sendCorporateEmployeeInvite(String employeeEmail, String username, String tempPassword) {
        log.debug("Notification delivery processed");
    }

    @Override
    public void sendCorporateBenefitsActivated(String employeeEmail, String dealName) {
        log.debug("Notification delivery processed");
    }

    private String buildInvoiceBody(InvoiceEmailDto dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p>Hello ").append(dto.toName() != null ? dto.toName() : "there").append(",</p>");
        sb.append("<p>Your Nirvana invoice is ready.</p>");
        sb.append("<ul>");
        sb.append("<li><strong>Invoice #</strong>: ").append(dto.invoiceNumber()).append("</li>");
        sb.append("<li><strong>Booking Reference</strong>: ").append(dto.bookingReference()).append("</li>");
        if (dto.bookingStartTs() != null) {
            sb.append("<li><strong>Scheduled</strong>: ").append(BookingEmailContent.formatStart(dto.bookingStartTs(), dto.spaTimezone())).append("</li>");
        }
        sb.append("<li><strong>Spa</strong>: ").append(dto.spaName()).append("</li>");
        sb.append("<li><strong>Service</strong>: ").append(dto.serviceName()).append("</li>");
        sb.append("<li><strong>Total</strong>: ").append(dto.currency()).append(" ").append(dto.totalCents() / 100.0).append("</li>");
        sb.append("</ul>");
        sb.append("<p>Download your invoice from your booking history: <a href=\"")
                .append(dto.invoicePdfUrl())
                .append("\">My bookings</a></p>");

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
