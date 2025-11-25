package com.nirvana.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnboardingDraftResponse {

    private Long id;
    private Long spaId;
    private String step;
    private String payload;
    private String resumeToken;
    private OffsetDateTime expiresAt;
    private OffsetDateTime lastClientEventAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private String clientRequestId;
}
