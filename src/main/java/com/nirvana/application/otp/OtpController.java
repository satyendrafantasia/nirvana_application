package com.nirvana.application.otp;

import com.nirvana.application.otp.dto.OtpSendRequest;
import com.nirvana.application.otp.dto.OtpSendResponse;
import com.nirvana.application.otp.dto.OtpVerifyRequest;
import com.nirvana.application.otp.dto.OtpVerifyResponse;
import com.nirvana.application.otp.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/auth/otp", "/api/auth/otp"})
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;

    @PostMapping("/send")
    public OtpSendResponse sendOtp(@Valid @RequestBody OtpSendRequest request) {
        return otpService.sendOtp(request.getPhoneNumber(), request.getEmail());
    }

    @PostMapping("/verify")
    public OtpVerifyResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return otpService.verifyOtp(request.getVerificationId(), request.getOtp());
    }
}
