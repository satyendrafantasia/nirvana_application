package com.nirvana.application.controller;

import com.nirvana.application.model.dto.ContentVariantListResponse;
import com.nirvana.application.model.dto.ContentVariantRequest;
import com.nirvana.application.model.dto.ContentVariantResponse;
import com.nirvana.application.service.ContentVariantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class ContentVariantController {

    private final ContentVariantService contentVariantService;

    @GetMapping("/content-variants")
    public ContentVariantListResponse listVariants(
            @RequestParam(value = "experimentKey", required = false) String experimentKey
    ) {
        return contentVariantService.listActiveVariants(experimentKey);
    }

    @PostMapping("/content-variants")
    public ContentVariantResponse createVariant(@Valid @RequestBody ContentVariantRequest request) {
        return contentVariantService.createVariant(request);
    }
}
