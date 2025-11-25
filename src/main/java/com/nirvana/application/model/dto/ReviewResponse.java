package com.nirvana.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {
    private Long id;
    private Long bookingId;
    private Long spaId;
    private Long userId;
    private Short rating;
    private String title;
    private String text;
    private Boolean isVisible;
    private OffsetDateTime createdAt;
}
