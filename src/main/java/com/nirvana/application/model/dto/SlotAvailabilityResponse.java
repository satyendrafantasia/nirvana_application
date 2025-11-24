// src/main/java/com/nirvana/application/model/dto/SlotAvailabilityResponse.java
package com.nirvana.application.model.dto;

import java.time.OffsetDateTime;

public record SlotAvailabilityResponse(
        Long id,
        OffsetDateTime startTs,
        OffsetDateTime endTs,
        short capacityUnit,
        short bookedUnits,
        short remainingUnits,
        boolean blocked,
        String status,
        String roomNumber
) {}
