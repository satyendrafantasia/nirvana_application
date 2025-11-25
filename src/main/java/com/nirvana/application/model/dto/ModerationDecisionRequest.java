package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.ModerationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ModerationDecisionRequest {

    @NotNull
    private ModerationStatus status;

    private String note;
}
