package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.PackageType;

public class PackageUsageCheckResponse {

    private boolean canUse;
    private String reason;
    private Integer remainingSessions;
    private PackageType packageType;
    private boolean spaEligible;

    public boolean isCanUse() {
        return canUse;
    }

    public void setCanUse(boolean canUse) {
        this.canUse = canUse;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getRemainingSessions() {
        return remainingSessions;
    }

    public void setRemainingSessions(Integer remainingSessions) {
        this.remainingSessions = remainingSessions;
    }

    public PackageType getPackageType() {
        return packageType;
    }

    public void setPackageType(PackageType packageType) {
        this.packageType = packageType;
    }

    public boolean isSpaEligible() {
        return spaEligible;
    }

    public void setSpaEligible(boolean spaEligible) {
        this.spaEligible = spaEligible;
    }
}
