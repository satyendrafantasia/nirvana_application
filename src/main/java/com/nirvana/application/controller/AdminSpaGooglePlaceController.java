package com.nirvana.application.controller;

import com.nirvana.application.model.dto.GooglePlaceLinkRequest;
import com.nirvana.application.model.dto.GooglePlaceLinkResponse;
import com.nirvana.application.service.GoogleBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/spas")
@RequiredArgsConstructor
@Tag(name = "Admin Spa Google Place", description = "Admin linkage of spas to Google Maps Place IDs")
public class AdminSpaGooglePlaceController {

    private final GoogleBookingService googleBookingService;

    @PutMapping("/{id}/google-place")
    @Operation(summary = "Link a spa to Google Place", description = "Bind or update the Google Maps place metadata for a spa")
    public GooglePlaceLinkResponse linkGooglePlace(@PathVariable Long id,
                                                   @Valid @RequestBody GooglePlaceLinkRequest request) {
        return googleBookingService.linkGooglePlace(id, request);
    }
}
