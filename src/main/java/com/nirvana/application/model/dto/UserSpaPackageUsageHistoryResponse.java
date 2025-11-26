package com.nirvana.application.model.dto;

import java.util.List;

public class UserSpaPackageUsageHistoryResponse {

    private Long subscriptionId;
    private Long spaId;
    private String spaName;
    private List<SpaPackageUsageItemResponse> usages;

    public UserSpaPackageUsageHistoryResponse() {
    }

    public UserSpaPackageUsageHistoryResponse(Long subscriptionId, Long spaId, String spaName, List<SpaPackageUsageItemResponse> usages) {
        this.subscriptionId = subscriptionId;
        this.spaId = spaId;
        this.spaName = spaName;
        this.usages = usages;
    }

    public Long getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(Long subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public Long getSpaId() {
        return spaId;
    }

    public void setSpaId(Long spaId) {
        this.spaId = spaId;
    }

    public String getSpaName() {
        return spaName;
    }

    public void setSpaName(String spaName) {
        this.spaName = spaName;
    }

    public List<SpaPackageUsageItemResponse> getUsages() {
        return usages;
    }

    public void setUsages(List<SpaPackageUsageItemResponse> usages) {
        this.usages = usages;
    }
}
