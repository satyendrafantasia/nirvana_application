package com.nirvana.application.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OtpLoginTokenResponse {
    private String loginToken;
}
