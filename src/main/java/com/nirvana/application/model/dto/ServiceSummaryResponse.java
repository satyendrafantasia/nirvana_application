package com.nirvana.application.model.dto;

// src/main/java/com/nirvana/application/model/dto/ServiceSummaryResponse.java

import com.nirvana.application.model.enums.GenderAllowed; // adjust enum name if different

public record ServiceSummaryResponse(
        Long id,
        String serviceCode,
        String name,
        String description,
        String category,
        String subCategory,
        Integer durationMinutes,
        Integer bufferMinutes,
        Integer minPersons,
        Integer maxPersons,
        String currency,
        Integer basePriceCents,
        Integer priceCents,
        Boolean isActive,
        Boolean isVisibleOnMarketplace,
        GenderAllowed genderAllowed
) {}

