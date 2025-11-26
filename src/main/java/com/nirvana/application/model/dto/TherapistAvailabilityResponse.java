package com.nirvana.application.model.dto;

public record TherapistAvailabilityResponse(
        Long therapistId,
        String name,
        String displayName,
        String profileImageUrl,
        String type,
        boolean available
) {}
