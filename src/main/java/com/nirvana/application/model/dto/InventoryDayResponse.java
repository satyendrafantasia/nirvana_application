package com.nirvana.application.model.dto;

import java.time.LocalDate;

public class InventoryDayResponse {

    private Long id;
    private Long spaId;
    private Long serviceId;
    private LocalDate serviceDate;
    private Integer availableUnits;
    private Integer basePriceCents;
    private Integer overridePriceCents;
    private String currency;
    private Boolean locked;
    private String note;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
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
