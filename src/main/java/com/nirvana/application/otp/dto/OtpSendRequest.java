package com.nirvana.application.otp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OtpSendRequest {

    @JsonProperty("phoneNumber")
    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Invalid phone number format")
    private String phoneNumber;

    @JsonProperty("email")
    @Email(message = "Invalid email format")
    private String email;

    @AssertTrue(message = "Either phoneNumber or email is required")
    public boolean isContactProvided() {
        return (phoneNumber != null && !phoneNumber.isBlank())
                || (email != null && !email.isBlank());
    }
}
