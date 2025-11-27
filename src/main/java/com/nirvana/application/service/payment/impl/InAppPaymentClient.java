package com.nirvana.application.service.payment.impl;

import com.nirvana.application.config.PaymentGatewayProperties;
import com.nirvana.application.model.enums.spa.PaymentStatus;
import com.nirvana.application.service.payment.PaymentClient;
import com.nirvana.application.service.payment.PaymentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class InAppPaymentClient implements PaymentClient {

    private static final String DEFAULT_FAILURE_REASON = "Payment gateway request failed";

    private final RestTemplateBuilder restTemplateBuilder;
    private final PaymentGatewayProperties gatewayProps;

    @Override
    public PaymentResult processPayment(Long userId, Long spaId, BigDecimal amount, String paymentMethod, String paymentReference) {
        PaymentResult fallbackResult = new PaymentResult(PaymentStatus.FAILED,
                paymentReference != null ? paymentReference : UUID.randomUUID().toString());

        if (!gatewayProps.isEnabled()) {
            log.warn("Payment gateway disabled; rejecting payment for spa {}", spaId);
            return fallbackResult;
        }

        if (gatewayProps.getBaseUrl() == null || gatewayProps.getBaseUrl().isBlank()) {
            log.error("Payment gateway base URL is not configured");
            return fallbackResult;
        }

        RestTemplate restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(gatewayProps.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(gatewayProps.getReadTimeoutMs()))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (gatewayProps.getApiKey() != null && !gatewayProps.getApiKey().isBlank()) {
            headers.setBearerAuth(gatewayProps.getApiKey());
        }

        GatewayPaymentRequest gatewayRequest = new GatewayPaymentRequest(userId, spaId, amount, paymentMethod, paymentReference);
        HttpEntity<GatewayPaymentRequest> entity = new HttpEntity<>(gatewayRequest, headers);

        try {
            String url = UriComponentsBuilder.fromHttpUrl(gatewayProps.getBaseUrl())
                    .path("/payments")
                    .toUriString();
            ResponseEntity<GatewayPaymentResponse> response = restTemplate.postForEntity(url, entity, GatewayPaymentResponse.class);
            GatewayPaymentResponse body = response.getBody();

            if (body == null) {
                log.error("Payment gateway returned empty response for spa {}", spaId);
                return fallbackResult;
            }

            PaymentStatus status = mapStatus(body.getStatus());
            String reference = body.getReferenceId() != null ? body.getReferenceId() : fallbackResult.getReferenceId();
            if (status == PaymentStatus.FAILED && body.getFailureReason() != null) {
                log.error("Payment gateway failed for spa {}: {}", spaId, body.getFailureReason());
            }
            log.info("Gateway processed payment for user {} spa {} amount {} with status {}", userId, spaId, amount, status);
            return new PaymentResult(status, reference);
        } catch (RestClientException e) {
            log.error("Payment gateway call failed for spa {}: {}", spaId, DEFAULT_FAILURE_REASON, e);
            return fallbackResult;
        }
    }

    private PaymentStatus mapStatus(String status) {
        if (status == null) {
            return PaymentStatus.FAILED;
        }
        return switch (status.toUpperCase()) {
            case "SUCCESS", "SUCCEEDED", "CAPTURED" -> PaymentStatus.SUCCESS;
            case "PENDING", "AUTHORIZED", "REQUIRES_ACTION" -> PaymentStatus.PENDING;
            default -> PaymentStatus.FAILED;
        };
    }

    private record GatewayPaymentRequest(Long userId, Long spaId, BigDecimal amount, String paymentMethod, String paymentReference) {
    }

    private static class GatewayPaymentResponse {
        private String status;
        private String referenceId;
        private String failureReason;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getReferenceId() {
            return referenceId;
        }

        public void setReferenceId(String referenceId) {
            this.referenceId = referenceId;
        }

        public String getFailureReason() {
            return failureReason;
        }

        public void setFailureReason(String failureReason) {
            this.failureReason = failureReason;
        }
    }
}
