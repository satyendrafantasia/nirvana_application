package com.nirvana.application.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class BuySpaPackageRequest {

    @NotNull
    private Long spaId;

    @NotNull
    private Long spaPackageId;

    @NotNull
    @Size(min = 2, max = 32)
    private String paymentMethod;

    @Size(max = 128)
    private String paymentReference;

    public Long getSpaId() {
        return spaId;
    }

    public void setSpaId(Long spaId) {
        this.spaId = spaId;
    }

    public Long getSpaPackageId() {
        return spaPackageId;
    }

    public void setSpaPackageId(Long spaPackageId) {
        this.spaPackageId = spaPackageId;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }
}
