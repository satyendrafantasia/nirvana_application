package com.nirvana.application.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class SlotHoldRequest {

    @NotNull
    private Long spaId;

    @NotNull
    private Long serviceId;

    @NotNull
    private Long slotId;

    @NotNull
    @Min(1)
    private Integer guests;

    @Min(1)
    @Max(30)
    private Integer holdMinutes = 10;

    public Long getSpaId() {
        return spaId;
    }

    public void setSpaId(Long spaId) {
        this.spaId = spaId;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    public Long getSlotId() {
        return slotId;
    }

    public void setSlotId(Long slotId) {
        this.slotId = slotId;
    }

    public Integer getGuests() {
        return guests;
    }

    public void setGuests(Integer guests) {
        this.guests = guests;
    }

    public Integer getHoldMinutes() {
        return holdMinutes;
    }

    public void setHoldMinutes(Integer holdMinutes) {
        this.holdMinutes = holdMinutes;
    }
}
