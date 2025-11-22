package com.nirvana.application.model.dto;

public record SpaSummaryResponse(
        Long id,
        String name,
        String city,
        String state,
        Float ratingAvg,
        Integer ratingCount,
        String thumbnailImageUrl,
        Integer startingPriceCents, // you can wire this later
        Double distanceKm) {}
