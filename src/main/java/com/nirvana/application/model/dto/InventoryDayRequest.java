package com.nirvana.application.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class InventoryDayRequest {

    @NotNull
    private Long spaId;

    private Long serviceId;

    @NotNull
    private LocalDate serviceDate;

    @NotNull
    @Min(0)
    private Integer availableUnits;

    @NotNull
    @Min(0)
    private Integer basePriceCents;

    @Min(0)
    private Integer overridePriceCents;

    private Boolean locked;

    private String note;

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

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public void setServiceDate(LocalDate serviceDate) {
        this.serviceDate = serviceDate;
    }

    public Integer getAvailableUnits() {
        return availableUnits;
    }

    public void setAvailableUnits(Integer availableUnits) {
        this.availableUnits = availableUnits;
    }

    public Integer getBasePriceCents() {
        return basePriceCents;
    }

    public void setBasePriceCents(Integer basePriceCents) {
        this.basePriceCents = basePriceCents;
    }

    public Integer getOverridePriceCents() {
        return overridePriceCents;
    }

    public void setOverridePriceCents(Integer overridePriceCents) {
        this.overridePriceCents = overridePriceCents;
    }

    public Boolean getLocked() {
        return locked;
    }

    public void setLocked(Boolean locked) {
        this.locked = locked;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
