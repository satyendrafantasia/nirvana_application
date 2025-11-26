package com.nirvana.application.controller;

import com.nirvana.application.model.dto.CreateOrUpdateSpaPackageRequest;
import com.nirvana.application.model.dto.SpaPackageResponse;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.SpaPackageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/spa/{spaId}/packages")
@RequiredArgsConstructor
@Validated
public class SpaPackageOwnerController {

    private final SpaPackageService spaPackageService;

    @PostMapping
    public SpaPackageResponse createOrUpdatePackage(@PathVariable("spaId") Long spaId,
                                                    @Valid @RequestBody CreateOrUpdateSpaPackageRequest request) {
        Long spaOwnerId = SecurityUtils.getCurrentUserId();
        return spaPackageService.createOrUpdateSpaPackage(spaOwnerId, spaId, request);
    }

    @GetMapping
    public List<SpaPackageResponse> listSpaPackages(@PathVariable("spaId") Long spaId) {
        Long spaOwnerId = SecurityUtils.getCurrentUserId();
        return spaPackageService.getSpaPackagesForOwner(spaOwnerId, spaId);
    }
}
