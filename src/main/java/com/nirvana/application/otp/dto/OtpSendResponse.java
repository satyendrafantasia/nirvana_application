package com.nirvana.application.otp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OtpSendResponse {

    @JsonProperty("verificationId")
    private final String verificationId;
}
