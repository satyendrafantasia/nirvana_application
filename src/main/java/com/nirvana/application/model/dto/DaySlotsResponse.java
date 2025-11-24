// src/main/java/com/nirvana/application/model/dto/DaySlotsResponse.java
package com.nirvana.application.model.dto;

import java.time.LocalDate;
import java.util.List;

public record DaySlotsResponse(
        LocalDate date,
        List<SlotAvailabilityResponse> slots
) {}
