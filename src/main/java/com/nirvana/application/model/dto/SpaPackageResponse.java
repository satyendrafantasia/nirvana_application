package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.spa.SpaPackageLevel;
import com.nirvana.application.model.enums.spa.SpaPackageStatus;

import java.math.BigDecimal;

public class SpaPackageResponse {

    private Long id;
    private Long spaId;
    private SpaPackageLevel level;
    private BigDecimal price;
    private Integer freeSessionsCount;
    private SpaPackageStatus status;

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

    public SpaPackageLevel getLevel() {
        return level;
    }

    public void setLevel(SpaPackageLevel level) {
        this.level = level;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getFreeSessionsCount() {
        return freeSessionsCount;
    }

    public void setFreeSessionsCount(Integer freeSessionsCount) {
        this.freeSessionsCount = freeSessionsCount;
    }

    public SpaPackageStatus getStatus() {
        return status;
    }

    public void setStatus(SpaPackageStatus status) {
        this.status = status;
    }
}
