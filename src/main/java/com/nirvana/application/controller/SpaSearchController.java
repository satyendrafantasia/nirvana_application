package com.nirvana.application.controller;


import com.nirvana.application.model.dto.PagedResponse;
import com.nirvana.application.model.dto.SpaSummaryResponse;
import com.nirvana.application.service.impl.SpaSearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for SPA search.
 *
 * Supports:
 *  - city / country / locality filters
 *  - free-text search
 *  - min rating
 *  - service-based search
 *  - manual lat/lon box
 *  - geo distance search (centerLat/centerLon/radiusKm + sort=distance)
 *  - pagination + sorting
 */
@RestController
@RequestMapping("/api/spas")
@RequiredArgsConstructor
@Validated
public class SpaSearchController {

    private final SpaSearchService spaSearchService;

    /**
     * Main search endpoint.
     *
     * Examples:
     *  GET /api/spas?city=Gurgaon&q=thai&minRating=4&sort=rating_desc&page=0&size=10
     *  GET /api/spas?centerLat=28.45&centerLon=77.03&radiusKm=5&sort=distance&page=0&size=10
     */
    @GetMapping("/search")
    public PagedResponse<SpaSummaryResponse> searchSpas(
            // Address / region filters
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false, name = "locality") String localityLike,

            // Free text over name/address/tags
            @RequestParam(required = false, name = "q") String freeText,

            // Manual bounding box (usually not needed if you use centerLat/centerLon/radiusKm)
            @RequestParam(required = false) Double minLat,
            @RequestParam(required = false) Double maxLat,
            @RequestParam(required = false) Double minLon,
            @RequestParam(required = false) Double maxLon,

            // Rating filter
            @RequestParam(required = false) Float minRating,

            // Service-based filters
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String serviceCategory,

            // Geo distance search (if all three are present & sort=distance)
            @RequestParam(required = false) Double centerLat,
            @RequestParam(required = false) Double centerLon,
            @RequestParam(required = false) @Positive Double radiusKm,

            // Sorting & pagination
            @RequestParam(required = false, defaultValue = "featured")
            String sort, // featured|rating_asc|rating_desc|name_asc|distance

            @RequestParam(defaultValue = "0") @Min(0)
            Integer page,

            @RequestParam(defaultValue = "10") @Min(1) @Max(50)
            Integer size
    ) {
        return spaSearchService.searchSpas(
                city,
                countryCode,
                localityLike,
                freeText,
                minLat,
                maxLat,
                minLon,
                maxLon,
                minRating,
                serviceId,
                serviceName,
                serviceCategory,
                centerLat,
                centerLon,
                radiusKm,
                sort,
                page,
                size
        );
    }
}

