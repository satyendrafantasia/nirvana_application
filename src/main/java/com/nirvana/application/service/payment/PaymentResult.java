package com.nirvana.application.service.payment;

import com.nirvana.application.model.enums.spa.PaymentStatus;

public class PaymentResult {

    private PaymentStatus status;
    private String referenceId;

    public PaymentResult() {
    }

    public PaymentResult(PaymentStatus status, String referenceId) {
        this.status = status;
        this.referenceId = referenceId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }
}
