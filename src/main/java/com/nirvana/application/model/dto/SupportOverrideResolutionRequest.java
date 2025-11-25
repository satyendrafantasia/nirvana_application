package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.SupportOverrideStatus;
import jakarta.validation.constraints.NotNull;

public class SupportOverrideResolutionRequest {

    @NotNull
    private SupportOverrideStatus status;

    private String resolvedBy;

    private String resolutionNotes;

    public SupportOverrideStatus getStatus() {
        return status;
    }

    public void setStatus(SupportOverrideStatus status) {
        this.status = status;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
    }
}
