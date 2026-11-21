package com.nirvana.application.controller;

import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.ListingClaimService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Spa owners claim their Google Maps listing; platform admins review the claims. */
@RestController
@RequiredArgsConstructor
public class ListingClaimController {
    private final ListingClaimService claims;
    private final com.nirvana.application.service.ListingClaimPhoneService phoneChecks;

    public record SearchRequest(@NotBlank @Size(min = 2, max = 200) String query,
                                @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String countryCode) {}

    public record RejectRequest(@NotBlank @Size(max = 1000) String reason) {}

    @PostMapping("/api/owner/claims/search")
    @PreAuthorize("@staffAccess.staff()")
    public ResponseEntity<List<Map<String, Object>>> search(@Valid @RequestBody SearchRequest request) {
        return live(claims.search(request.query().strip(), request.countryCode()));
    }

    @GetMapping("/api/owner/claims/places/{placeId}")
    @PreAuthorize("@staffAccess.staff()")
    public ResponseEntity<Map<String, Object>> prefill(@PathVariable String placeId) {
        if (!placeId.matches("[A-Za-z0-9_-]{1,255}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Place ID.");
        return live(claims.prefill(placeId));
    }

    public record VerifyCodeRequest(@NotBlank @Size(max = 64) String verificationId,
                                    @NotBlank @Pattern(regexp = "\\d{6}", message = "Enter the 6-digit code") String code) {}

    /** Texts a code to the phone number on the Google listing, not one the owner typed. */
    @PostMapping("/api/owner/claims/places/{placeId}/phone-code")
    @PreAuthorize("@staffAccess.staff()")
    public Map<String, Object> sendPhoneCode(@PathVariable String placeId) {
        if (!placeId.matches("[A-Za-z0-9_-]{1,255}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Place ID.");
        return phoneChecks.send(SecurityUtils.getCurrentUserId(), placeId);
    }

    @PostMapping("/api/owner/claims/phone-code/verify")
    @PreAuthorize("@staffAccess.staff()")
    public Map<String, Object> verifyPhoneCode(@Valid @RequestBody VerifyCodeRequest request) {
        return phoneChecks.verify(SecurityUtils.getCurrentUserId(), request.verificationId(), request.code());
    }

    @PostMapping("/api/owner/claims")
    @PreAuthorize("@staffAccess.staff()")
    public Map<String, Object> submit(@Valid @RequestBody ListingClaimService.ClaimRequest request, HttpServletRequest http) {
        try {
            return claims.submit(SecurityUtils.getCurrentUserId(), request, http.getRemoteAddr(), http.getHeader("User-Agent"));
        } catch (DataIntegrityViolationException e) {
            // Two owners submitted the same listing at once; the unique Place ID lets only one through.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This spa is already on Nirvana or has a claim under review. If it is yours, contact partner support.");
        }
    }

    @GetMapping("/api/admin/listing-claims")
    @PreAuthorize("@staffAccess.admin()")
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "PENDING") String status) {
        if (!List.of("PENDING", "APPROVED", "REJECTED").contains(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status.");
        return claims.list(status);
    }

    @PostMapping("/api/admin/listing-claims/{id}/approve")
    @PreAuthorize("@staffAccess.admin()")
    public Map<String, String> approve(@PathVariable long id) {
        claims.approve(id, SecurityUtils.getCurrentUserId());
        return Map.of("status", "APPROVED");
    }

    @PostMapping("/api/admin/listing-claims/{id}/reject")
    @PreAuthorize("@staffAccess.admin()")
    public Map<String, String> reject(@PathVariable long id, @Valid @RequestBody RejectRequest request) {
        claims.reject(id, SecurityUtils.getCurrentUserId(), request.reason().strip());
        return Map.of("status", "REJECTED");
    }

    private static <T> ResponseEntity<T> live(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
