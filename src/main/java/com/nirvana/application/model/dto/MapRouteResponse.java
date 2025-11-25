package com.nirvana.application.model.dto;

public record MapRouteResponse(
        double originLat,
        double originLon,
        double destinationLat,
        double destinationLon,
        double distanceKm,
        long estimatedDurationSeconds,
        String mode,
        String provider
) {
}
