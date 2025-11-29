package com.nirvana.application.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OtpLoginCompleteRequest {
    @NotBlank
    private String loginToken;
}
