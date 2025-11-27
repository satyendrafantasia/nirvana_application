package com.nirvana.application.controller;

import com.nirvana.application.model.dto.MediaAssetResponse;
import com.nirvana.application.model.dto.MediaPresignRequest;
import com.nirvana.application.model.dto.MediaPresignResponse;
import com.nirvana.application.model.dto.SpaMediaAttachRequest;
import com.nirvana.application.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Media", description = "Pre-signed URL generation and media attachments")
public class MediaController {

    private final MediaService mediaService;

    @PostMapping("/media/presign")
    @Operation(summary = "Generate S3 pre-signed URL", description = "Generates a pre-signed PUT URL for direct client uploads to S3.", security = {@SecurityRequirement(name = "bearerAuth")})
    @ResponseStatus(HttpStatus.OK)
    public MediaPresignResponse presign(@Valid @RequestBody MediaPresignRequest request) {
        return mediaService.generatePresignedUpload(request);
    }

    @PostMapping("/spa/{spaId}/media")
    @Operation(summary = "Confirm uploaded media", description = "Confirms an S3 upload and attaches metadata to the spa.", security = {@SecurityRequirement(name = "bearerAuth")})
    @ApiResponse(responseCode = "201", description = "Media attached", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MediaAssetResponse.class)))
    @ResponseStatus(HttpStatus.CREATED)
    public MediaAssetResponse attachMedia(@PathVariable Long spaId, @Valid @RequestBody SpaMediaAttachRequest request) {
        return mediaService.confirmUpload(spaId, request);
    }
}
