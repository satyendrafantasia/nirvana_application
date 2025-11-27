package com.nirvana.application.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "BookingRescheduleRequest", description = "Payload to reschedule an existing booking.")
public class BookingRescheduleRequest {

    @NotNull
    @Schema(description = "New slot identifier the booking should move to", example = "9100")
    private Long targetSlotId;

    @Schema(description = "Reason for reschedule", example = "Client requested earlier slot")
    private String reason;

    @Schema(description = "Allow rescheduling within same day even if cut-off applies", example = "false")
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
