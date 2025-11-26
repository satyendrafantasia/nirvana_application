package com.nirvana.application.exception;

import jakarta.persistence.EntityNotFoundException;
import com.nirvana.application.exception.NoActivePackageException;
import com.nirvana.application.exception.NoRemainingSessionsException;
import com.nirvana.application.exception.PackageNotEligibleForSpaException;
import com.nirvana.application.exception.PackagePaymentFailedException;
import com.nirvana.application.exception.SpaNotInPackageException;
import java.util.HashMap;
import java.util.Map;
import com.nirvana.application.exception.NoActiveSpaPackageForUserException;
import com.nirvana.application.exception.PaymentFailedException;
import com.nirvana.application.exception.SpaNotFoundException;
import com.nirvana.application.exception.SpaPackageInactiveException;
import com.nirvana.application.exception.SpaPackageNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class LegacyExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler({
            NoActivePackageException.class,
            NoRemainingSessionsException.class,
            PackageNotEligibleForSpaException.class,
            SpaNotInPackageException.class,
            SpaPackageInactiveException.class,
            NoActiveSpaPackageForUserException.class
    })
    public ResponseEntity<Map<String, String>> handlePackageValidation(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(PackagePaymentFailedException.class)
    public ResponseEntity<Map<String, String>> handlePackagePayment(PackagePaymentFailedException ex) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ResponseEntity<Map<String, String>> handleSpaPackagePayment(PaymentFailedException ex) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler({SpaNotFoundException.class, SpaPackageNotFoundException.class})
    public ResponseEntity<Map<String, String>> handleSpaNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getMessage()));
    }
}
