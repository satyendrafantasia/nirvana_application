package com.nirvana.application.auth.controller;

import com.nirvana.application.auth.dto.CompleteRegistrationRequest;
import com.nirvana.application.auth.dto.OtpRequestDto;
import com.nirvana.application.auth.dto.RegistrationAuthResponse;
import com.nirvana.application.auth.dto.RegistrationTokenResponse;
import com.nirvana.application.auth.dto.VerificationResponse;
import com.nirvana.application.auth.dto.VerifyOtpRequest;
import com.nirvana.application.auth.service.OtpService;
import com.nirvana.application.auth.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration flow using OTP:
 * <ul>
 *     <li>{@code /request-otp}: start a verification session and send OTP to phone/email.</li>
 *     <li>{@code /verify-otp}: validate the OTP and issue a short-lived registration token.</li>
 *     <li>{@code /complete}: exchange the registration token for an account and JWTs.</li>
 * </ul>
 */
@RestController
@RequestMapping({"/auth/register", "/api/auth/register"})
@RequiredArgsConstructor
public class OtpRegistrationController {

    private final OtpService otpService;
    private final RegistrationService registrationService;

    @PostMapping("/request-otp")
    public VerificationResponse requestOtp(@Valid @RequestBody OtpRequestDto request) {
        return otpService.requestOtp(request);
    }

    @PostMapping("/verify-otp")
    public RegistrationTokenResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return otpService.verifyOtp(request);
    }

    @PostMapping("/complete")
    public RegistrationAuthResponse complete(@Valid @RequestBody CompleteRegistrationRequest request) {
        return registrationService.completeRegistration(request);
    }
}
