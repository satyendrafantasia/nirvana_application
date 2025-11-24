// src/main/java/com/nirvana/application/model/dto/SpaOnboardingDetailsRequest.java
package com.nirvana.application.model.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpaOnboardingDetailsRequest {

    private String description;
    private String websiteUrl;
    private String facebookUrl;
    private String instagramUrl;

    private String[] amenities;
    private String[] tags;

    private Integer maxConcurrentServices;
    private Integer maxAdvanceBookingDays;
    private Integer minNoticeMinutes;
}
