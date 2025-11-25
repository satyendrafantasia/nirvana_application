// src/main/java/com/nirvana/application/service/impl/NoOpEmailService.java
package com.nirvana.application.service.impl;

import com.nirvana.application.model.dto.InvoiceEmailDto;
import com.nirvana.application.model.NotificationLog;
import com.nirvana.application.model.enums.NotificationChannel;
import com.nirvana.application.model.enums.NotificationStatus;
import com.nirvana.application.repository.NotificationLogRepository;
import com.nirvana.application.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "notifications.sendgrid", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpEmailService implements EmailService {

    private final NotificationLogRepository notificationLogRepository;

    @Override
    public void sendInvoiceEmail(InvoiceEmailDto dto) {
        NotificationLog logEntry = NotificationLog.builder()
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.SENT)
                .destination(dto.getToEmail())
                .title("Invoice " + dto.getInvoiceNumber())
                .message("Invoice ready for booking " + dto.getBookingReference())
                .build();
        notificationLogRepository.save(logEntry);
        log.info("Recorded invoice email log for {} (sendgrid disabled)", dto.getToEmail());

    }
}
