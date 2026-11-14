package com.nirvana.application;

import com.nirvana.application.service.impl.DynamicPricingService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class CouponValidationTest {
    @Test
    void unrecognizedCouponStopsCheckoutBeforePayment() {
        var pricing = new DynamicPricingService();
        var problem = assertThrows(ResponseStatusException.class,
                () -> pricing.evaluatePricing(null, null, null, null, 1, 10000, "unknown", false));
        assertEquals(400, problem.getStatusCode().value());
    }
}
