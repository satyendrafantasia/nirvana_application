// src/main/java/com/nirvana/application/service/SpaOnboardingService.java
package com.nirvana.application.service;

import com.nirvana.application.model.dto.*;

import java.util.List;

public interface SpaOnboardingService {

    SpaOnboardingSummaryResponse startOnboarding(Long ownerUserId,
                                                 SpaOnboardingStartRequest request);

    SpaOnboardingSummaryResponse updateDetails(Long ownerUserId,
                                               Long spaId,
                                               SpaOnboardingDetailsRequest request);

    SpaOnboardingSummaryResponse updateAddress(Long ownerUserId,
                                               Long spaId,
                                               AddressDTO addressDto);

    SpaOnboardingSummaryResponse submitKyc(Long ownerUserId,
                                           Long spaId,
                                           SpaKycRequest request);

    List<SpaOnboardingSummaryResponse> listMySpas(Long ownerUserId);

    SpaOnboardingSummaryResponse getMySpa(Long ownerUserId, Long spaId);
}
