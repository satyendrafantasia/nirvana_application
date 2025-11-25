package com.nirvana.application.service;

import com.nirvana.application.model.dto.PagedResponse;
import com.nirvana.application.model.dto.SpaSummaryResponse;
import com.nirvana.application.service.impl.SpaSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpaRecommendationService {

    private static final double DEFAULT_RADIUS_KM = 5.0;

    private final SpaSearchService spaSearchService;

    public PagedResponse<SpaSummaryResponse> recommendNearbySpas(double lat,
                                                                 double lon,
                                                                 Double radiusKm,
                                                                 Float minRating,
                                                                 Integer page,
                                                                 Integer size) {
        double searchRadius = (radiusKm == null || radiusKm <= 0) ? DEFAULT_RADIUS_KM : radiusKm;
        return spaSearchService.searchSpas(
                null, // city
                null, // countryCode
                null, // localityLike
                null, // freeText
                null, // minLat
                null, // maxLat
                null, // minLon
                null, // maxLon
                minRating,
                null, // serviceId
                null, // serviceName
                null, // serviceCategory
                lat,
                lon,
                searchRadius,
                "distance",
                page,
                size
        );
    }
}
