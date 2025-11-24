// src/main/java/com/nirvana/application/model/dto/CreateBookingRequest.java
package com.nirvana.application.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBookingRequest(

        @NotNull
        @Min(1)
        Integer guestCount,

        @Size(max = 2000)
        String customerNotes,

        // for internal/testing bookings
        Boolean testBooking,

        // optional – if you support coupons
        @Size(max = 64)
        String couponCode,

        // optional – can be filled from X-Forwarded-For etc.
        @Size(max = 64)
        String clientIp
) {}
