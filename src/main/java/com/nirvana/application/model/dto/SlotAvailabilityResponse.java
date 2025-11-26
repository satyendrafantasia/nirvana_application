// src/main/java/com/nirvana/application/model/dto/SlotAvailabilityResponse.java
package com.nirvana.application.model.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record SlotAvailabilityResponse(
        Long slotId,
        OffsetDateTime startTs,
        OffsetDateTime endTs,
        int remainingCapacityUnits,
        String roomNumber,
        String status,
        boolean therapistSelectionEnabled,
        List<TherapistAvailabilityResponse> therapists
) {}
