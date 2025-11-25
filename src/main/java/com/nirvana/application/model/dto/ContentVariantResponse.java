package com.nirvana.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentVariantResponse {
    private Long id;
    private String experimentKey;
    private String variantKey;
    private String content;
    private Boolean isActive;
}
