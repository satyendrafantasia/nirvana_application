package com.nirvana.application.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "BuySpaPackageRequest", description = "Payload to purchase a spa-specific package.")
public class BuySpaPackageRequest {

    @NotNull
    @Schema(description = "Spa identifier where package is being purchased", example = "101")
    private Long spaId;

    @NotNull
    @Schema(description = "Spa package identifier", example = "501")
    private Long spaPackageId;

    @NotNull
    @Size(min = 2, max = 32)
    @Schema(description = "Payment method used for purchase", example = "RAZORPAY")
    private String paymentMethod;

    @Size(max = 128)
    @Schema(description = "Reference or transaction id from payment gateway", example = "pay_123456")
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
