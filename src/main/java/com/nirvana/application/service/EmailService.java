// src/main/java/com/nirvana/application/service/EmailService.java
package com.nirvana.application.service;

import com.nirvana.application.model.dto.CheckoutReminderEmail;
import com.nirvana.application.model.dto.InvoiceEmailDto;

public interface EmailService {
    void sendInvoiceEmail(InvoiceEmailDto dto);

    void sendCorporateEmployeeInvite(String employeeEmail);

    void sendCorporateBenefitsActivated(String employeeEmail, String dealName);

    /** Sends a reminder about a checkout left unpaid. Returns true when the mail provider accepted it. */
    default boolean sendCheckoutReminder(CheckoutReminderEmail email) {
        return false;
    }
}
