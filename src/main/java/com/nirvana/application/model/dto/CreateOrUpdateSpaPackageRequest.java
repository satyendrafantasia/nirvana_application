package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.spa.SpaPackageLevel;
import com.nirvana.application.model.enums.spa.SpaPackageStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class CreateOrUpdateSpaPackageRequest {
    @lombok.Getter @lombok.Setter @jakarta.validation.constraints.Size(max=100) private String title;
    @lombok.Getter @lombok.Setter @jakarta.validation.constraints.Size(max=1500) private String description;
    @lombok.Getter @lombok.Setter @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(3650) private Integer validityDays;
    @lombok.Getter @lombok.Setter private Boolean archived;
    @lombok.Getter @lombok.Setter @jakarta.validation.constraints.Size(max=500) private java.util.Set<Long> serviceIds;

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
