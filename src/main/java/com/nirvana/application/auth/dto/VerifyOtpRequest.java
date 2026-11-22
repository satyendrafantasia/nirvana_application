package com.nirvana.application.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** The code the user typed, or, when Firebase checked a phone code, Google's signed proof instead. */
@Data
public class VerifyOtpRequest {

    @NotBlank
    private String verificationId;

    @Pattern(regexp = "^\\d{6}$", message = "OTP must be a 6 digit code")
    private String otp;

    @Size(max = 4096)
    private String firebaseIdToken;

    @JsonIgnore
    @AssertTrue(message = "Enter the 6-digit code")
    public boolean isCodeOrPhoneProof() {
        return (otp != null && !otp.isBlank()) || (firebaseIdToken != null && !firebaseIdToken.isBlank());
    }
}
