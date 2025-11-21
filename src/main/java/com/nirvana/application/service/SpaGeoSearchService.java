// java
package com.nirvana.application.service;

import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.repository.projection.SpaDistanceProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpaGeoSearchService {

    private final SpaRepository spaRepository;

    /**
     * Finds spas within radiusKm kilometers of (lat, lon), paged and sorted by distance.
     */
    public Page<SpaDistanceProjection> findNearbySpas(double lat,
                                                      double lon,
                                                      double radiusKm,
                                                      int page,
                                                      int size) {
        if (radiusKm <= 0) {
            throw new IllegalArgumentException("radiusKm must be > 0");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0");
        }

        // Rough conversion: 1 degree latitude ≈ 111 km
        double latRadius = radiusKm / 111.0;
        double minLat = lat - latRadius;
        double maxLat = lat + latRadius;

        double lonRadius = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));
        double minLon = lon - lonRadius;
        double maxLon = lon + lonRadius;

        Pageable pageable = PageRequest.of(page, size);

        return spaRepository.findSpasWithinRadiusKmAndBoxPaged(
                lat, lon, radiusKm, minLat, maxLat, minLon, maxLon, pageable
        );
    }
}
