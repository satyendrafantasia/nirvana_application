package com.nirvana.application.controller;

import com.nirvana.application.model.corporate.CorporateEmployee;
import com.nirvana.application.model.dto.corporate.*;
import com.nirvana.application.service.corporate.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/corporates")
@RequiredArgsConstructor
public class CorporateController {

    private final CorporateService corporateService;
    private final CorporateDealService corporateDealService;
    private final CorporateOnboardingService corporateOnboardingService;
    private final CorporateEmployeeService corporateEmployeeService;

    @PostMapping
    public CorporateResponse createCorporate(@Valid @RequestBody CreateCorporateRequest request) {
        return corporateService.createCorporate(request);
    }

    @GetMapping
    public List<CorporateResponse> listCorporates() {
        return corporateService.listCorporates();
    }

    @PostMapping("/{corporateId}/deals")
    public CorporateDealResponse createDeal(@PathVariable Long corporateId, @Valid @RequestBody CreateCorporateDealRequest request) {
        return corporateDealService.createCorporateDeal(corporateId, request);
    }

    @GetMapping("/{corporateId}/deals")
    public List<CorporateDealResponse> listDeals(@PathVariable Long corporateId) {
        return corporateDealService.listCorporateDeals(corporateId);
    }

    @PostMapping("/{corporateId}/deals/{dealId}/payment/confirm")
    public CorporateDealResponse confirmDealPayment(@PathVariable Long corporateId, @PathVariable Long dealId) {
        return corporateDealService.confirmPayment(corporateId, dealId);
    }

    @PostMapping(value = "/{corporateId}/deals/{dealId}/employees/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CorporateEmployeeUploadResponse uploadEmployees(@PathVariable Long corporateId,
                                                           @PathVariable Long dealId,
                                                           @RequestPart("file") MultipartFile file) {
        return corporateOnboardingService.uploadEmployeeFile(corporateId, dealId, file);
    }

    @GetMapping("/{corporateId}/onboarding/uploads")
    public List<CorporateOnboardingUploadStatusResponse> listOnboardingUploads(@PathVariable Long corporateId) {
        return corporateOnboardingService.listUploads(corporateId);
    }

    @GetMapping("/{corporateId}/employees")
    public List<CorporateEmployee> getEmployees(@PathVariable Long corporateId) {
        return corporateEmployeeService.getEmployeesByCorporate(corporateId);
    }
}
