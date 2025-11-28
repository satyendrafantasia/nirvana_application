package com.nirvana.application.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ErrorResponse {
    private String error;
    private String message;
    private Map<String, Object> details;
}
