// src/main/java/com/nirvana/application/model/dto/WeekSlotsResponse.java
package com.nirvana.application.model.dto;

import java.util.List;

public record WeekSlotsResponse(
        Long spaId,
        Long serviceId,
        List<DaySlotsResponse> days
) {}
