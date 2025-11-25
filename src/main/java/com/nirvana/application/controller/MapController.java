package com.nirvana.application.controller;

import com.nirvana.application.config.MapProviderProperties;
import com.nirvana.application.model.dto.MapProviderConfigResponse;
import com.nirvana.application.model.dto.MapRouteResponse;
import com.nirvana.application.service.MapRoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/maps")
@RequiredArgsConstructor
public class MapController {

    private final MapRoutingService mapRoutingService;
    private final MapProviderProperties mapProviderProperties;

    @GetMapping("/route")
    public MapRouteResponse estimateRoute(@RequestParam double originLat,
                                          @RequestParam double originLon,
                                          @RequestParam double destinationLat,
                                          @RequestParam double destinationLon,
                                          @RequestParam(required = false, defaultValue = "driving") String mode) {
        return mapRoutingService.estimateRoute(originLat, originLon, destinationLat, destinationLon, mode);
    }

    @GetMapping("/eta/spa/{spaId}")
    public MapRouteResponse etaToSpa(@PathVariable Long spaId,
                                     @RequestParam double originLat,
                                     @RequestParam double originLon,
                                     @RequestParam(required = false, defaultValue = "driving") String mode) {
        return mapRoutingService.estimateRouteToSpa(spaId, originLat, originLon, mode);
    }

    @GetMapping("/provider")
    public MapProviderConfigResponse providerConfig() {
        return new MapProviderConfigResponse(
                mapProviderProperties.getProvider(),
                mapProviderProperties.getActiveApiKey(),
                mapProviderProperties.getActiveBaseUrl(),
                mapProviderProperties.getActiveStaticStyle()
        );
    }
}
