package com.nirvana.application.controller;

import com.nirvana.application.model.corporate.CorporateEmployee;
import com.nirvana.application.model.dto.corporate.*;
import com.nirvana.application.service.corporate.*;
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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/corporates")
@RequiredArgsConstructor
@Tag(name = "Corporate", description = "Corporate onboarding, deal management and employee eligibility")
@SecurityRequirement(name = "bearerAuth")
public class CorporateController {

    private final CorporateService corporateService;
    private final CorporateDealService corporateDealService;
    private final CorporateOnboardingService corporateOnboardingService;
    private final CorporateEmployeeService corporateEmployeeService;

    @PostMapping
    @Operation(summary = "Create corporate", description = "Onboard a new corporate partner and configure billing preferences.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Corporate created", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CorporateResponse.class)))
    })
    public CorporateResponse createCorporate(@Valid @RequestBody CreateCorporateRequest request) {
        return corporateService.createCorporate(request);
    }

    @GetMapping
    @Operation(summary = "List corporates", description = "Retrieve corporate partners configured in Nirvana.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Corporates returned", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = CorporateResponse.class))))
    })
    public List<CorporateResponse> listCorporates() {
        return corporateService.listCorporates();
    }

    @PostMapping("/{corporateId}/deals")
    @Operation(summary = "Create corporate deal", description = "Define benefits such as discounted bookings or package access for a corporate.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Deal created", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CorporateDealResponse.class)))
    })
    public CorporateDealResponse createDeal(@Parameter(description = "Corporate identifier") @PathVariable Long corporateId, @Valid @RequestBody CreateCorporateDealRequest request) {
        return corporateDealService.createCorporateDeal(corporateId, request);
    }

    @GetMapping("/{corporateId}/deals")
    @Operation(summary = "List corporate deals", description = "List active deals and coupons configured for a corporate.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deals returned", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = CorporateDealResponse.class))))
    })
    public List<CorporateDealResponse> listDeals(@Parameter(description = "Corporate identifier") @PathVariable Long corporateId) {
        return corporateDealService.listCorporateDeals(corporateId);
    }

    @PostMapping("/{corporateId}/deals/{dealId}/payment/confirm")
    @Operation(summary = "Confirm corporate deal payment", description = "Mark a corporate deal as paid after invoice settlement.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment confirmed", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CorporateDealResponse.class)))
    })
    public CorporateDealResponse confirmDealPayment(@Parameter(description = "Corporate identifier") @PathVariable Long corporateId, @Parameter(description = "Deal identifier") @PathVariable Long dealId) {
        return corporateDealService.confirmPayment(corporateId, dealId);
    }

    @PostMapping(value = "/{corporateId}/deals/{dealId}/employees/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload employees", description = "Bulk upload eligible employees for a corporate deal via spreadsheet.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Upload accepted", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CorporateEmployeeUploadResponse.class)))
    })
    public CorporateEmployeeUploadResponse uploadEmployees(@Parameter(description = "Corporate identifier") @PathVariable Long corporateId,
                                                           @Parameter(description = "Deal identifier") @PathVariable Long dealId,
                                                           @RequestPart("file") MultipartFile file) {
        return corporateOnboardingService.uploadEmployeeFile(corporateId, dealId, file);
    }

    @GetMapping("/{corporateId}/onboarding/uploads")
    @Operation(summary = "List onboarding uploads", description = "Track status of employee file uploads for a corporate.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Uploads returned", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = CorporateOnboardingUploadStatusResponse.class))))
    })
    public List<CorporateOnboardingUploadStatusResponse> listOnboardingUploads(@Parameter(description = "Corporate identifier") @PathVariable Long corporateId) {
        return corporateOnboardingService.listUploads(corporateId);
    }

    @GetMapping("/{corporateId}/employees")
    @Operation(summary = "List corporate employees", description = "List employees enrolled under a corporate including benefit eligibility.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Employees returned", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = CorporateEmployee.class))))
    })
    public List<CorporateEmployee> getEmployees(@Parameter(description = "Corporate identifier") @PathVariable Long corporateId) {
        return corporateEmployeeService.getEmployeesByCorporate(corporateId);
    }
}
