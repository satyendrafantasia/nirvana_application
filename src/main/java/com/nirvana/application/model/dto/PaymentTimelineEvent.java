package com.nirvana.application.model.dto;

import java.time.OffsetDateTime;

public record PaymentTimelineEvent(
        String type,
        String detail,
        OffsetDateTime occurredAt
) {}
