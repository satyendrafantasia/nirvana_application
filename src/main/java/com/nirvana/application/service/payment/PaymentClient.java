package com.nirvana.application.service.payment;

import java.math.BigDecimal;

public interface PaymentClient {

    PaymentResult processPayment(Long userId, Long spaId, BigDecimal amount, String paymentMethod, String paymentReference);
}
