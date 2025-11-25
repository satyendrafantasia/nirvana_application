package com.nirvana.application.model.dto;

import jakarta.validation.constraints.NotNull;

public class BookingRescheduleRequest {

    @NotNull
    private Long targetSlotId;

    private String reason;

    private Boolean allowSameDayChange;

    public Long getTargetSlotId() {
        return targetSlotId;
    }

    public void setTargetSlotId(Long targetSlotId) {
        this.targetSlotId = targetSlotId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Boolean getAllowSameDayChange() {
        return allowSameDayChange;
    }

    public void setAllowSameDayChange(Boolean allowSameDayChange) {
        this.allowSameDayChange = allowSameDayChange;
    }
}
