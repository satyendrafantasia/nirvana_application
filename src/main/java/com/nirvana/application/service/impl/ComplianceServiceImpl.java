package com.nirvana.application.service.impl;

import com.nirvana.application.model.User;
import com.nirvana.application.model.dto.ConsentUpdateRequest;
import com.nirvana.application.model.dto.DataErasureRequest;
import com.nirvana.application.repository.UserRepository;
import com.nirvana.application.service.ComplianceService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceServiceImpl implements ComplianceService {

    private static final String ERASURE_CONFIRMATION = "ERASE";

    private final UserRepository userRepository;

    @Override
    @Transactional
    public User recordConsent(Long userId, ConsentUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        if (Boolean.TRUE.equals(request.getAcceptPrivacyPolicy())) {
            user.setPrivacyConsentedAt(OffsetDateTime.now());
        }
        user.setPrivacyConsentVersion(request.getConsentVersion());
        user.setConsentSource(Optional.ofNullable(request.getConsentSource()).orElse("self-service"));
        if (request.getMarketingOptIn() != null) {
            user.setMarketingOptIn(request.getMarketingOptIn());
        }
        if (request.getTimezone() != null && !request.getTimezone().isBlank()) {
            user.setTimezone(request.getTimezone());
        }
        if (request.getLocale() != null && !request.getLocale().isBlank()) {
            user.setLocale(request.getLocale());
        }

        return userRepository.save(user);
    }

    @Override
    @Transactional
    public User eraseUserData(Long userId, DataErasureRequest request) {
        if (!ERASURE_CONFIRMATION.equalsIgnoreCase(request.getConfirmationKeyword())) {
            throw new IllegalArgumentException("Confirmation keyword mismatch");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        user.setDataErasureRequestedAt(OffsetDateTime.now());
        user.setDataErasedAt(OffsetDateTime.now());
        user.setEmail(null);
        user.setPhone(null);
        user.setName("Erased");
        user.setLastName(null);
        user.setDisplayName(null);
        user.setUsername("erased-" + userId + "-" + UUID.randomUUID());
        user.setMarketingOptIn(false);
        user.setActive(false);
        user.setIsActive(false);
        user.setDeletedAt(OffsetDateTime.now());
        user.setPrivacyConsentVersion(null);
        user.setPrivacyConsentedAt(null);
        user.setConsentSource("erasure:" + request.getReason());

        log.info("User {} marked for data erasure. Reason: {}", userId, request.getReason());
        return userRepository.save(user);
    }
}
