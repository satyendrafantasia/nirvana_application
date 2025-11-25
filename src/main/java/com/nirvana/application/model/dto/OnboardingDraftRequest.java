package com.nirvana.application.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class OnboardingDraftRequest {

    private Long spaId;

    @NotBlank
    @Size(max = 64)
    private String step;

    @NotBlank
    private String payload;

    @Size(max = 64)
    private String resumeToken;

    private Long version;

    private OffsetDateTime clientEventAt;

    @Size(max = 64)
    private String clientRequestId;
}
