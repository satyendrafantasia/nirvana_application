package com.nirvana.application.model.dto;

import java.util.List;

public class PackageUsageHistoryResponse {

    private List<PackageUsageLogItemResponse> usages;

    public PackageUsageHistoryResponse() {
    }

    public PackageUsageHistoryResponse(List<PackageUsageLogItemResponse> usages) {
        this.usages = usages;
    }

    public List<PackageUsageLogItemResponse> getUsages() {
        return usages;
    }

    public void setUsages(List<PackageUsageLogItemResponse> usages) {
        this.usages = usages;
    }
}
