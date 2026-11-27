package com.nirvana.application.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class VerificationResponse {
    private String verificationId;
    private boolean firebasePhone;

    public VerificationResponse(String verificationId) {
        this.verificationId = verificationId;
    }
}
