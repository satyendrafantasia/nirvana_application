package com.nirvana.application.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DataErasureRequest {

    @NotBlank
    @Size(max = 256)
    private String reason;

    @NotBlank
    @Size(max = 16)
    private String confirmationKeyword;
}
