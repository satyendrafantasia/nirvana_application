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
                .message(com.nirvana.application.config.SentryScrubbing.scrub(message) + (correlationId != null ? " (correlationId=" + correlationId + ")" : ""))
                .path(path);
    }


    private static String route(HttpServletRequest request) {
        Object pattern = request.getAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern == null ? "/" : pattern.toString();
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException ex,
                                                           HttpServletRequest request) {
        String cid = org.slf4j.MDC.get("correlationId");
        log.info("event=request.rejected reason=not_found");
        ApiErrorResponse body = base(HttpStatus.NOT_FOUND, ex.getMessage(), route(request), cid)
                .fieldErrors(null)
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusiness(BusinessException ex,
                                                           HttpServletRequest request) {
        String cid = org.slf4j.MDC.get("correlationId");
        log.info("event=request.rejected reason=business_rule type={}", ex.getClass().getSimpleName());
        ApiErrorResponse body = base(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), route(request), cid)
                .fieldErrors(null)
                .build();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                         HttpServletRequest request) {
        String cid = org.slf4j.MDC.get("correlationId");
        List<ApiErrorResponse.FieldErrorDetails> fieldErrors = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .map(error -> {
                    String field = error instanceof FieldError fe ? fe.getField() : error.getObjectName();
                    String message = error.getDefaultMessage();
                    return ApiErrorResponse.FieldErrorDetails.builder()
                            .field(field)
                            .message(message)
                            .build();
                })
                .collect(Collectors.toList());

        ApiErrorResponse body = base(HttpStatus.BAD_REQUEST, "Validation failed", route(request), cid)
                .fieldErrors(fieldErrors)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                      HttpServletRequest request) {
        String cid = org.slf4j.MDC.get("correlationId");
        List<ApiErrorResponse.FieldErrorDetails> fieldErrors = ex.getConstraintViolations()
                .stream()
                .map(v -> ApiErrorResponse.FieldErrorDetails.builder()
                        .field(v.getPropertyPath().toString())
                        .message(v.getMessage())
                        .build())
                .collect(Collectors.toList());

        ApiErrorResponse body = base(HttpStatus.BAD_REQUEST, "Constraint violation", route(request), cid)
                .fieldErrors(fieldErrors)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({CorporateNotFoundException.class, CorporateDealNotFoundException.class, CorporateEmployeeNotFoundException.class, CorporateCouponNotFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleCorporateNotFound(RuntimeException ex, HttpServletRequest request) {
        String cid = org.slf4j.MDC.get("correlationId");
        ApiErrorResponse body = base(HttpStatus.NOT_FOUND, ex.getMessage(), route(request), cid)
                .fieldErrors(null)
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler({CorporateCouponExpiredException.class, CorporateCouponExhaustedException.class, NoActiveCorporateCouponException.class, CorporateOnboardingParseException.class, CorporatePaymentPendingException.class})
    public ResponseEntity<ApiErrorResponse> handleCorporateValidation(RuntimeException ex, HttpServletRequest request) {
        String cid = org.slf4j.MDC.get("correlationId");
        ApiErrorResponse body = base(HttpStatus.BAD_REQUEST, ex.getMessage(), route(request), cid)
                .fieldErrors(null)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                                 HttpServletRequest request) {
        String cid = org.slf4j.MDC.get("correlationId");
        log.warn("event=request.rejected reason=concurrent_update");
        ApiErrorResponse body = base(HttpStatus.CONFLICT, "Conflict: resource was modified by another request",
                route(request), cid)
                .fieldErrors(null)
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> forbidden(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(403).body(base(HttpStatus.FORBIDDEN, "Access denied", route(request), null).build());
    }

    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> entityMissing(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(404).body(base(HttpStatus.NOT_FOUND, ex.getMessage(), route(request), null).build());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiErrorResponse> invalidOperation(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(409).body(base(HttpStatus.CONFLICT, ex.getMessage(), route(request), null).build());
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> statusException(org.springframework.web.server.ResponseStatusException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatusCode()).body(base(HttpStatus.valueOf(ex.getStatusCode().value()), ex.getReason(), route(request), null).build());
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> unreadable(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(base(HttpStatus.BAD_REQUEST, "Invalid request body", route(request), null).build());
    }

    /** A failed sign-in (wrong password, unknown or disabled account) is the caller's problem, not a server error. */
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> authenticationFailed(Exception ex, HttpServletRequest request) {
        // One message for every cause, so the response does not reveal which accounts exist.
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(base(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.", route(request), null).build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex,
                                                          HttpServletRequest request) {
        String cid = org.slf4j.MDC.get("correlationId");
        log.error("event=request.failed type={} origin={}", ex.getClass().getSimpleName(),
                ex.getStackTrace().length == 0 ? "unknown" : ex.getStackTrace()[0]);
        ApiErrorResponse body = base(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                route(request), cid)
                .fieldErrors(null)
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
