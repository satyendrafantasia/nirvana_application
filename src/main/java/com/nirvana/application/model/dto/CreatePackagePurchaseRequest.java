package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.PackageType;
import com.nirvana.application.model.enums.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreatePackagePurchaseRequest {

    @NotNull
    private PackageType packageType;

    @NotNull
    private PaymentStatus paymentStatus;

    @Size(max = 128)
    private String paymentReference;

    public PackageType getPackageType() {
        return packageType;
    }

    public void setPackageType(PackageType packageType) {
        this.packageType = packageType;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }
}
