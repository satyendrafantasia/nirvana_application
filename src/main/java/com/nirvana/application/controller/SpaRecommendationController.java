package com.nirvana.application.controller;

import com.nirvana.application.model.dto.PagedResponse;
import com.nirvana.application.model.dto.SpaSummaryResponse;
import com.nirvana.application.service.SpaRecommendationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/spas")
@RequiredArgsConstructor
@Validated
public class SpaRecommendationController {

    private final SpaRecommendationService spaRecommendationService;

    @GetMapping("/recommendations")
    public PagedResponse<SpaSummaryResponse> recommendNearby(@RequestParam double lat,
                                                             @RequestParam double lon,
                                                             @RequestParam(required = false) Double radiusKm,
                                                             @RequestParam(required = false) Float minRating,
                                                             @RequestParam(defaultValue = "0") @Min(0) Integer page,
                                                             @RequestParam(defaultValue = "5") @Min(1) @Max(50) Integer size) {
        return spaRecommendationService.recommendNearbySpas(lat, lon, radiusKm, minRating, page, size);
    }
}
