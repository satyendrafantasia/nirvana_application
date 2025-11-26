package com.nirvana.application.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpiPaymentConfirmRequest {
    @NotBlank
    private String paymentIntentId;

    @NotBlank
    private String transactionReference;

    private String payerVpa;

    private boolean success = true;
}
