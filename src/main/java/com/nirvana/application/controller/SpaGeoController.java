package com.nirvana.application.controller;

import com.nirvana.application.repository.projection.SpaDistanceProjection;
import com.nirvana.application.service.SpaGeoSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/spas")
@RequiredArgsConstructor
public class SpaGeoController {

    private final SpaGeoSearchService spaGeoSearchService;

    @GetMapping("/nearby")
    public Page<SpaDistanceProjection> findNearby(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam double radiusKm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return spaGeoSearchService.findNearbySpas(lat, lon, radiusKm, page, size);
    }
}
