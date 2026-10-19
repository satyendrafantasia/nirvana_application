package com.nirvana.application.controller;

import com.nirvana.application.model.User;
import com.nirvana.application.model.dto.ConsentUpdateRequest;
import com.nirvana.application.model.dto.DataErasureRequest;
import com.nirvana.application.security.UserPrincipal;
import com.nirvana.application.service.ComplianceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/privacy")
@RequiredArgsConstructor
public class ComplianceController {

    private final ComplianceService complianceService;

    @PostMapping("/consent")
    public java.util.Map<String,String> updateConsent(@Valid @RequestBody ConsentUpdateRequest request) {
        complianceService.recordConsent(resolveUserId(), request);
        return java.util.Map.of("message", "Privacy preferences saved");
    }

    @PostMapping("/erase")
    public java.util.Map<String,String> erase(@Valid @RequestBody DataErasureRequest request) {
        complianceService.eraseUserData(resolveUserId(), request);
        return java.util.Map.of("message", "Your personal data has been erased");
    }

    private Long resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getId();
        }
        throw new IllegalStateException("Unauthenticated request");
    }
}
