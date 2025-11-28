package com.nirvana.application.otp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OtpVerifyRequest {

    @NotBlank(message = "verificationId is required")
    @JsonProperty("verificationId")
    private String verificationId;

    @NotBlank(message = "otp is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP must be a 6-digit number")
    @JsonProperty("otp")
    private String otp;
}
