package com.nirvana.application.service;

import com.nirvana.application.config.MapProviderProperties;
import com.nirvana.application.exception.NotFoundException;
import com.nirvana.application.model.Address;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.dto.MapRouteResponse;
import com.nirvana.application.repository.SpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MapRoutingService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private final SpaRepository spaRepository;
    private final MapProviderProperties mapProviderProperties;

    public MapRouteResponse estimateRoute(double originLat,
                                          double originLon,
                                          double destinationLat,
                                          double destinationLon,
                                          String mode) {
        String travelMode = (mode == null || mode.isBlank()) ? "driving" : mode.toLowerCase();
        double distanceKm = haversineDistance(originLat, originLon, destinationLat, destinationLon);
        long durationSeconds = estimateDurationSeconds(distanceKm, travelMode);

        return new MapRouteResponse(
                originLat,
                originLon,
                destinationLat,
                destinationLon,
                distanceKm,
                durationSeconds,
                travelMode,
                mapProviderProperties.getProvider()
        );
    }

    public MapRouteResponse estimateRouteToSpa(Long spaId,
                                               double originLat,
                                               double originLon,
                                               String mode) {
        Spa spa = spaRepository.findById(spaId)
                .orElseThrow(() -> new NotFoundException("Spa not found: " + spaId));

        Address address = spa.getAddress();
        if (address == null || address.getLatitude() == null || address.getLongitude() == null) {
            throw new IllegalStateException("Spa " + spaId + " is missing geocoordinates for routing");
        }

        return estimateRoute(
                originLat,
                originLon,
                address.getLatitude().doubleValue(),
                address.getLongitude().doubleValue(),
                mode
        );
    }

    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    private long estimateDurationSeconds(double distanceKm, String mode) {
        double speedKph = switch (mode) {
            case "walking" -> 5.0;
            case "cycling", "bicycling" -> 15.0;
            case "transit" -> 28.0; // metro/bus blended
            default -> 38.0; // driving urban default
        };

        double hours = distanceKm / speedKph;
        return Math.max(60L, Math.round(hours * 3600));
    }
}
