// src/main/java/com/nirvana/application/model/dto/RazorpayConfirmRequest.java
package com.nirvana.application.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RazorpayConfirmRequest {

    @NotBlank
    @JsonProperty("razorpay_payment_id")
    private String razorpayPaymentId;

    @NotBlank
    @JsonProperty("razorpay_order_id")
    private String razorpayOrderId;

    @NotBlank
    @JsonProperty("razorpay_signature")
    private String razorpaySignature;
}
