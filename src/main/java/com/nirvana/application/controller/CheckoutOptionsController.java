package com.nirvana.application.controller;

import com.nirvana.application.config.RazorpayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class CheckoutOptionsController {
    private final RazorpayProperties properties;

    @GetMapping("/api/bookings/payment-options")
    public Map<String, Boolean> paymentOptions() {
        return Map.of("online", properties.readyForCheckout(), "offline", true);
    }
}
