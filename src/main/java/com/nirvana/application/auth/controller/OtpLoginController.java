package com.nirvana.application.auth.controller;

import com.nirvana.application.auth.dto.OtpLoginCompleteRequest;
import com.nirvana.application.auth.dto.OtpLoginTokenResponse;
import com.nirvana.application.auth.dto.OtpRequestDto;
import com.nirvana.application.auth.dto.VerificationResponse;
import com.nirvana.application.auth.dto.VerifyOtpRequest;
import com.nirvana.application.auth.service.OtpAuthService;
import com.nirvana.application.model.dto.AuthResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/auth/login/otp", "/api/auth/login/otp"})
@RequiredArgsConstructor
public class OtpLoginController {

    private final OtpAuthService otpAuthService;

    @PostMapping("/request")
    public VerificationResponse requestLoginOtp(@Valid @RequestBody OtpRequestDto request) {
        return otpAuthService.requestLoginOtp(request);
    }

    @PostMapping("/verify")
    public OtpLoginTokenResponse verifyLoginOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return otpAuthService.verifyLoginOtp(request);
    }

    @PostMapping("/complete")
    public AuthResponse completeLogin(@Valid @RequestBody OtpLoginCompleteRequest request) {
        return otpAuthService.completeOtpLogin(request);
    }
}
