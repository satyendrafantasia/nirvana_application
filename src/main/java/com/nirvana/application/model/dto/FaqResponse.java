package com.nirvana.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FaqResponse {
    private Long id;
    private String question;
    private String answer;
    private String category;
    private Boolean isActive;
}
