// src/main/java/com/nirvana/application/controller/SpaController.java
package com.nirvana.application.controller;

import com.nirvana.application.model.dto.ServiceSummaryResponse;
import com.nirvana.application.model.dto.SpaDetailResponse;
import com.nirvana.application.model.dto.SpaRequestDTO;
import com.nirvana.application.model.dto.SpaResponseDTO;
import com.nirvana.application.service.impl.SpaReadService;
import com.nirvana.application.service.SpaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/spas")
@RequiredArgsConstructor
@Tag(name = "Spa", description = "Spa onboarding, details and service catalogs")
public class SpaController {

    private final SpaService spaService;       // your existing write/CRUD service
    private final SpaReadService spaReadService; // new read-oriented service

    @PostMapping
    @Operation(summary = "Create spa", description = "Onboard a new spa location with core profile and contact details.", security = {@SecurityRequirement(name = "bearerAuth")})
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Spa created", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SpaResponseDTO.class)))
    })
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public SpaResponseDTO createSpa(@Valid @RequestBody SpaRequestDTO request) {
        return spaService.createSpa(request);
    }

    // SPA DETAILS for detail screen
    @GetMapping("/{id}")
    @Operation(summary = "Get spa details", description = "Fetch spa profile, amenities and imagery for detail screens.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Spa details", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SpaDetailResponse.class))),
            @ApiResponse(responseCode = "404", description = "Spa not found", content = @Content(schema = @Schema(implementation = com.nirvana.application.api.ApiErrorResponse.class)))
    })
    public SpaDetailResponse getSpa(@Parameter(description = "Spa identifier") @PathVariable Long id) {
        return spaReadService.getSpaDetails(id);
    }

    // SPA SERVICES for service list on detail screen
    @GetMapping("/{id}/services")
    @Operation(summary = "List spa services", description = "List bookable services offered by a spa, for use on detail screens.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Spa services", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = ServiceSummaryResponse.class))))
    })
    public List<ServiceSummaryResponse> getSpaServices(@Parameter(description = "Spa identifier") @PathVariable Long id) {
        return spaReadService.getSpaServices(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update spa", description = "Update spa profile information and amenities.", security = {@SecurityRequirement(name = "bearerAuth")})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Spa updated", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SpaResponseDTO.class)))
    })
    public SpaResponseDTO updateSpa(@Parameter(description = "Spa identifier") @PathVariable Long id,
                                    @Valid @RequestBody SpaRequestDTO request) {
        return spaService.updateSpa(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate spa", description = "Soft deactivate a spa from discovery and booking.", security = {@SecurityRequirement(name = "bearerAuth")})
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Spa deactivated")
    })
    public void deactivateSpa(@Parameter(description = "Spa identifier") @PathVariable Long id) {
        spaService.deactivateSpa(id);
    }
}
