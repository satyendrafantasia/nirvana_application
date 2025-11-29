package com.nirvana.application.auth.controller;

import com.nirvana.application.auth.dto.CompletePasswordResetRequest;
import com.nirvana.application.auth.dto.OtpRequestDto;
import com.nirvana.application.auth.dto.PasswordResetTokenResponse;
import com.nirvana.application.auth.dto.VerificationResponse;
import com.nirvana.application.auth.dto.VerifyOtpRequest;
import com.nirvana.application.auth.service.OtpAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/auth/password", "/api/auth/password"})
@RequiredArgsConstructor
public class PasswordResetController {

    private final OtpAuthService otpAuthService;

    @PostMapping("/request-otp")
    public VerificationResponse requestReset(@Valid @RequestBody OtpRequestDto request) {
        return otpAuthService.requestPasswordResetOtp(request);
    }

    @PostMapping("/verify-otp")
    public PasswordResetTokenResponse verifyReset(@Valid @RequestBody VerifyOtpRequest request) {
        return otpAuthService.verifyPasswordResetOtp(request);
    }

    @PostMapping("/reset")
    public void resetPassword(@Valid @RequestBody CompletePasswordResetRequest request) {
        otpAuthService.completePasswordReset(request);
    }
}
