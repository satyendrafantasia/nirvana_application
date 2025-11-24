// src/main/java/com/nirvana/application/service/impl/NoOpEmailService.java
package com.nirvana.application.service.impl;

import com.nirvana.application.model.dto.InvoiceEmailDto;
import com.nirvana.application.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NoOpEmailService implements EmailService {

    @Override
    public void sendInvoiceEmail(InvoiceEmailDto dto) {
        log.info("[NO-OP EMAIL] Would send invoice {} to {}");

    }
}
