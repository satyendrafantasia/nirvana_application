// src/main/java/com/nirvana/application/model/dto/SpaOnboardingSummaryResponse.java
package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.KycStatus;
import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpaOnboardingSummaryResponse {

    private Long id;
    private String name;
    private String city;
    private String countryCode;

    private boolean active;
    private boolean verified;
    private boolean featured;

    private KycStatus kycStatus;
    private OffsetDateTime kycRequestedAt;
    private OffsetDateTime kycApprovedAt;
    private OffsetDateTime kycRejectedAt;

    private String kycRejectedReason;

    // some basic metrics
    private Float ratingAvg;
    private Integer ratingCount;
}
