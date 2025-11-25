package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.SlotStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public class ProviderAvailabilitySyncRequest {

    @NotNull
    private SlotStatus status;

    @Min(0)
    private Short bookedUnits;

    @Min(1)
    private Short capacityUnit;

    private OffsetDateTime providerHoldExpiresAt;

    public SlotStatus getStatus() {
        return status;
    }

    public void setStatus(SlotStatus status) {
        this.status = status;
    }

    public Short getBookedUnits() {
        return bookedUnits;
    }

    public void setBookedUnits(Short bookedUnits) {
        this.bookedUnits = bookedUnits;
    }

    public Short getCapacityUnit() {
        return capacityUnit;
    }

    public void setCapacityUnit(Short capacityUnit) {
        this.capacityUnit = capacityUnit;
    }

    public OffsetDateTime getProviderHoldExpiresAt() {
        return providerHoldExpiresAt;
    }

    public void setProviderHoldExpiresAt(OffsetDateTime providerHoldExpiresAt) {
        this.providerHoldExpiresAt = providerHoldExpiresAt;
    }
}
