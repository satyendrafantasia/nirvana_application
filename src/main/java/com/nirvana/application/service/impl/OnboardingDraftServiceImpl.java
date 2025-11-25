package com.nirvana.application.service.impl;

import com.nirvana.application.model.OnboardingDraft;
import com.nirvana.application.model.dto.OnboardingDraftRequest;
import com.nirvana.application.model.dto.OnboardingDraftResponse;
import com.nirvana.application.repository.OnboardingDraftRepository;
import com.nirvana.application.service.OnboardingDraftService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnboardingDraftServiceImpl implements OnboardingDraftService {

    private static final Duration HOLD_DURATION = Duration.ofHours(48);

    private final OnboardingDraftRepository repository;

    @Override
    @Transactional
    public OnboardingDraftResponse saveDraft(Long ownerUserId, OnboardingDraftRequest request) {
        OnboardingDraft draft = request.getResumeToken() != null && !request.getResumeToken().isBlank()
                ? loadDraft(ownerUserId, request.getResumeToken())
                : new OnboardingDraft();

        if (request.getVersion() != null && draft.getVersion() != null && !request.getVersion().equals(draft.getVersion())) {
            throw new IllegalStateException("Draft version mismatch. Refresh and retry.");
        }

        if (draft.getId() == null) {
            draft.setOwnerUserId(ownerUserId);
            draft.setResumeToken(UUID.randomUUID().toString());
        }

        draft.setSpaId(request.getSpaId());
        draft.setStepKey(request.getStep());
        draft.setPayload(request.getPayload());
        draft.setExpiresAt(OffsetDateTime.now().plus(HOLD_DURATION));
        draft.setLastClientEventAt(request.getClientEventAt());
        draft.setClientRequestId(request.getClientRequestId());

        OnboardingDraft saved = repository.save(draft);

        log.info("Saved onboarding draft token={} owner={} step={} spaId={} clientRequestId={} version={}"
                , saved.getResumeToken(), ownerUserId, saved.getStepKey(), saved.getSpaId(), saved.getClientRequestId(), saved.getVersion());

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OnboardingDraftResponse getDraft(Long ownerUserId, String resumeToken) {
        OnboardingDraft draft = loadDraft(ownerUserId, resumeToken);
        validateNotExpired(draft);
        return toResponse(draft);
    }

    @Override
    @Transactional
    public OnboardingDraftResponse heartbeat(Long ownerUserId, String resumeToken) {
        OnboardingDraft draft = loadDraft(ownerUserId, resumeToken);
        validateNotExpired(draft);

        draft.setExpiresAt(OffsetDateTime.now().plus(HOLD_DURATION));
        OnboardingDraft saved = repository.save(draft);
        log.info("Heartbeat extended draft token={} owner={} until {}", saved.getResumeToken(), ownerUserId, saved.getExpiresAt());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteDraft(Long ownerUserId, String resumeToken) {
        OnboardingDraft draft = loadDraft(ownerUserId, resumeToken);
        repository.delete(draft);
        log.info("Deleted onboarding draft token={} owner={} step={} spaId={}", resumeToken, ownerUserId, draft.getStepKey(), draft.getSpaId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OnboardingDraftResponse> listDrafts(Long ownerUserId) {
        OffsetDateTime now = OffsetDateTime.now();
        return repository.findByOwnerUserId(ownerUserId).stream()
                .filter(d -> d.getExpiresAt() == null || d.getExpiresAt().isAfter(now))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private OnboardingDraft loadDraft(Long ownerUserId, String resumeToken) {
        return repository.findByResumeTokenAndOwnerUserId(resumeToken, ownerUserId)
                .orElseThrow(() -> new EntityNotFoundException("Draft not found for token: " + resumeToken));
    }

    private void validateNotExpired(OnboardingDraft draft) {
        if (draft.getExpiresAt() != null && draft.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalStateException("Draft has expired. Request a new hold.");
        }
    }

    private OnboardingDraftResponse toResponse(OnboardingDraft draft) {
        return OnboardingDraftResponse.builder()
                .id(draft.getId())
                .spaId(draft.getSpaId())
                .step(draft.getStepKey())
                .payload(draft.getPayload())
                .resumeToken(draft.getResumeToken())
                .expiresAt(draft.getExpiresAt())
                .lastClientEventAt(draft.getLastClientEventAt())
                .updatedAt(draft.getUpdatedAt())
                .version(draft.getVersion())
                .clientRequestId(draft.getClientRequestId())
                .build();
    }
}
