package com.nirvana.application.api;

import com.nirvana.application.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private ApiErrorResponse.ApiErrorResponseBuilder base(HttpStatus status,
                                                          String message,
                                                          String path,
                                                          String correlationId) {
        return ApiErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message + (correlationId != null ? " (correlationId=" + correlationId + ")" : ""))
                .path(path);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException ex,
                                                           HttpServletRequest request) {
        String cid = UUID.randomUUID().toString();
        log.info("[{}] NotFound: {}", cid, ex.getMessage());
        ApiErrorResponse body = base(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI(), cid)
                .fieldErrors(null)
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusiness(BusinessException ex,
                                                           HttpServletRequest request) {
        String cid = UUID.randomUUID().toString();
        log.info("[{}] Business error: {}", cid, ex.getMessage());
        ApiErrorResponse body = base(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request.getRequestURI(), cid)
                .fieldErrors(null)
                .build();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                         HttpServletRequest request) {
        String cid = UUID.randomUUID().toString();
        List<ApiErrorResponse.FieldErrorDetails> fieldErrors = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .map(error -> {
                    String field = error instanceof FieldError fe ? fe.getField() : error.getObjectName();
                    Object rejectedValue = error instanceof FieldError fe ? fe.getRejectedValue() : null;
                    String message = error.getDefaultMessage();
                    return ApiErrorResponse.FieldErrorDetails.builder()
                            .field(field)
                            .message(message)
                            .rejectedValue(rejectedValue)
                            .build();
                })
                .collect(Collectors.toList());

        ApiErrorResponse body = base(HttpStatus.BAD_REQUEST, "Validation failed", request.getRequestURI(), cid)
                .fieldErrors(fieldErrors)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                      HttpServletRequest request) {
        String cid = UUID.randomUUID().toString();
        List<ApiErrorResponse.FieldErrorDetails> fieldErrors = ex.getConstraintViolations()
                .stream()
                .map(v -> ApiErrorResponse.FieldErrorDetails.builder()
                        .field(v.getPropertyPath().toString())
                        .message(v.getMessage())
                        .rejectedValue(v.getInvalidValue())
                        .build())
                .collect(Collectors.toList());

        ApiErrorResponse body = base(HttpStatus.BAD_REQUEST, "Constraint violation", request.getRequestURI(), cid)
                .fieldErrors(fieldErrors)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({CorporateNotFoundException.class, CorporateDealNotFoundException.class, CorporateEmployeeNotFoundException.class, CorporateCouponNotFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleCorporateNotFound(RuntimeException ex, HttpServletRequest request) {
        String cid = UUID.randomUUID().toString();
        ApiErrorResponse body = base(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI(), cid)
                .fieldErrors(null)
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler({CorporateCouponExpiredException.class, CorporateCouponExhaustedException.class, NoActiveCorporateCouponException.class, CorporateOnboardingParseException.class})
    public ResponseEntity<ApiErrorResponse> handleCorporateValidation(RuntimeException ex, HttpServletRequest request) {
        String cid = UUID.randomUUID().toString();
        ApiErrorResponse body = base(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI(), cid)
                .fieldErrors(null)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                                 HttpServletRequest request) {
        String cid = UUID.randomUUID().toString();
        log.warn("[{}] Optimistic lock failure: {}", cid, ex.getMessage());
        ApiErrorResponse body = base(HttpStatus.CONFLICT, "Conflict: resource was modified by another request",
                request.getRequestURI(), cid)
                .fieldErrors(null)
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex,
                                                          HttpServletRequest request) {
        String cid = UUID.randomUUID().toString();
        log.error("[{}] Unhandled exception at {}: ", cid, request.getRequestURI(), ex);
        ApiErrorResponse body = base(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                request.getRequestURI(), cid)
                .fieldErrors(null)
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
