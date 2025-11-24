// src/main/java/com/nirvana/application/service/EmailService.java
package com.nirvana.application.service;

import com.nirvana.application.model.dto.InvoiceEmailDto;

public interface EmailService {
    void sendInvoiceEmail(InvoiceEmailDto dto);
}
