package com.nirvana.application.model.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public class BlackoutWindowRequest {

    @NotNull
    private Long spaId;

    private Long providerId;

    @NotNull
    private OffsetDateTime startTs;

    @NotNull
    private OffsetDateTime endTs;

    private String reason;

    private String createdBy;

    public Long getSpaId() {
        return spaId;
    }

    public void setSpaId(Long spaId) {
        this.spaId = spaId;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public OffsetDateTime getStartTs() {
        return startTs;
    }

    public void setStartTs(OffsetDateTime startTs) {
        this.startTs = startTs;
    }

    public OffsetDateTime getEndTs() {
        return endTs;
    }

    public void setEndTs(OffsetDateTime endTs) {
        this.endTs = endTs;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
