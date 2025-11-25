package com.nirvana.application.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "onboarding_draft", indexes = {
        @Index(name = "idx_onboarding_draft_owner", columnList = "owner_user_id"),
        @Index(name = "idx_onboarding_draft_expires", columnList = "expires_at")
})
public class OnboardingDraft extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "spa_id")
    private Long spaId;

    @Column(name = "step_key", nullable = false, length = 64)
    private String stepKey;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(name = "resume_token", nullable = false, unique = true, length = 64)
    private String resumeToken;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "last_client_event_at")
    private OffsetDateTime lastClientEventAt;

    @Column(name = "client_request_id", length = 64)
    private String clientRequestId;

    @Version
    private Long version;
}
