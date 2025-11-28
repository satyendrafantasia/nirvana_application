package com.nirvana.application.otp;

import com.nirvana.application.otp.dto.ErrorResponse;
import com.nirvana.application.otp.exception.EmailSendException;
import com.nirvana.application.otp.exception.InvalidOtpException;
import com.nirvana.application.otp.exception.MaxAttemptsExceededException;
import com.nirvana.application.otp.exception.OtpExpiredException;
import com.nirvana.application.otp.exception.OtpNotFoundException;
import com.nirvana.application.otp.exception.RateLimitExceededException;
import com.nirvana.application.otp.exception.SmsSendException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = OtpController.class)
public class OtpExceptionHandler {

    @ExceptionHandler(OtpNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(OtpNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.builder()
                        .error("OTP_NOT_FOUND")
                        .message(ex.getMessage())
                        .build());
    }

    @ExceptionHandler({InvalidOtpException.class, OtpExpiredException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.builder()
                        .error(ex instanceof OtpExpiredException ? "OTP_EXPIRED" : "INVALID_OTP")
                        .message(ex.getMessage())
                        .build());
    }

    @ExceptionHandler({MaxAttemptsExceededException.class, RateLimitExceededException.class})
    public ResponseEntity<ErrorResponse> handleTooManyRequests(RuntimeException ex) {
        String code = ex instanceof MaxAttemptsExceededException ? "MAX_ATTEMPTS_EXCEEDED" : "OTP_RATE_LIMITED";
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse.builder()
                        .error(code)
                        .message(ex.getMessage())
                        .build());
    }

    @ExceptionHandler({SmsSendException.class, EmailSendException.class})
    public ResponseEntity<ErrorResponse> handleProviderFailure(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.builder()
                        .error("OTP_DELIVERY_FAILED")
                        .message(ex.getMessage())
                        .details(Map.of("provider", ex instanceof SmsSendException ? "sms" : "email"))
                        .build());
    }
}
