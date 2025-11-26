package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.spa.SpaPackageLevel;
import com.nirvana.application.model.enums.spa.UserSpaPackageStatus;

public class SpaPackageUsageCheckResponse {

    private boolean canUse;
    private String reason;
    private Long subscriptionId;
    private SpaPackageLevel level;
    private Integer remainingSessions;
    private UserSpaPackageStatus status;

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

    public Long getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(Long subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public SpaPackageLevel getLevel() {
        return level;
    }

    public void setLevel(SpaPackageLevel level) {
        this.level = level;
    }

    public Integer getRemainingSessions() {
        return remainingSessions;
    }

    public void setRemainingSessions(Integer remainingSessions) {
        this.remainingSessions = remainingSessions;
    }

    public UserSpaPackageStatus getStatus() {
        return status;
    }

    public void setStatus(UserSpaPackageStatus status) {
        this.status = status;
    }
}
