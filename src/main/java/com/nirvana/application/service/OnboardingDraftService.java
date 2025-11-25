package com.nirvana.application.service;

import com.nirvana.application.model.dto.OnboardingDraftRequest;
import com.nirvana.application.model.dto.OnboardingDraftResponse;

import java.util.List;

public interface OnboardingDraftService {

    OnboardingDraftResponse saveDraft(Long ownerUserId, OnboardingDraftRequest request);

    OnboardingDraftResponse getDraft(Long ownerUserId, String resumeToken);

    OnboardingDraftResponse heartbeat(Long ownerUserId, String resumeToken);

    void deleteDraft(Long ownerUserId, String resumeToken);

    List<OnboardingDraftResponse> listDrafts(Long ownerUserId);
}
