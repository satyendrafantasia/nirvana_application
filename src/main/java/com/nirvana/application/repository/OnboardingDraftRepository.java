package com.nirvana.application.repository;

import com.nirvana.application.model.OnboardingDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OnboardingDraftRepository extends JpaRepository<OnboardingDraft, Long> {

    Optional<OnboardingDraft> findByResumeTokenAndOwnerUserId(String resumeToken, Long ownerUserId);

    List<OnboardingDraft> findByOwnerUserId(Long ownerUserId);
}
