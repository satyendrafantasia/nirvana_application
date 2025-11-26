package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.spa.SpaPackageLevel;
import com.nirvana.application.model.enums.spa.SpaPackageStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class CreateOrUpdateSpaPackageRequest {

    @NotNull
    private SpaPackageLevel level;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal price;

    @NotNull
    @Min(1)
    private Integer freeSessionsCount;

    @NotNull
    private SpaPackageStatus status;

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
