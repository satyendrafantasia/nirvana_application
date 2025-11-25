// src/main/java/com/nirvana/application/model/dto/SlotAvailabilityResponse.java
package com.nirvana.application.model.dto;

import java.time.OffsetDateTime;

public record SlotAvailabilityResponse(
        Long slotId,
        OffsetDateTime startTs,
        OffsetDateTime endTs,
        int remainingCapacityUnits,
        String roomNumber,
        String status
) {}
