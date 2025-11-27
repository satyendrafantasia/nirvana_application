package com.nirvana.application.model.dto;

import java.util.List;

public record PaymentTimelineResponse(
        Long bookingId,
        String bookingReference,
        String gateway,
        List<PaymentTimelineEvent> events
) {}
