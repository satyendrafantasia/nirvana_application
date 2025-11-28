package com.nirvana.application.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class OtpRequestDto {

    @Pattern(regexp = "^\+[1-9]\d{1,14}$", message = "Phone number must be in E.164 format")
    private String phoneNumber;

    @Email(message = "Email should be valid")
    private String email;
}
