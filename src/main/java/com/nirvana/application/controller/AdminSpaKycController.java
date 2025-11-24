// src/main/java/com/nirvana/application/controller/AdminSpaKycController.java
package com.nirvana.application.controller;

import com.nirvana.application.model.Spa;
import com.nirvana.application.model.enums.KycStatus;
import com.nirvana.application.repository.SpaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/admin/spas")
@RequiredArgsConstructor
public class AdminSpaKycController {

    private final SpaRepository spaRepository;

    @PostMapping("/{spaId}/kyc/approve")
    public void approveKyc(@PathVariable Long spaId) {
        Spa spa = spaRepository.findById(spaId)
                .orElseThrow(() -> new EntityNotFoundException("Spa not found " + spaId));

        spa.setKycStatus(KycStatus.VERIFIED);
        spa.setKycApprovedAt(OffsetDateTime.now(ZoneOffset.UTC));
        spa.setIsVerified(true);
        spa.setIsActive(true); // go live

        spaRepository.save(spa);
    }

    @PostMapping("/{spaId}/kyc/reject")
    public void rejectKyc(@PathVariable Long spaId,
                          @RequestParam String reason) {
        Spa spa = spaRepository.findById(spaId)
                .orElseThrow(() -> new EntityNotFoundException("Spa not found " + spaId));

        spa.setKycStatus(KycStatus.REJECTED);
        spa.setKycRejectedAt(OffsetDateTime.now(ZoneOffset.UTC));
        spa.setKycRejectedReason(reason);
        spa.setIsVerified(false);
        spa.setIsActive(false);

        spaRepository.save(spa);
    }
}
