package com.nirvana.application.model.dto;

import java.util.List;

public class SpaPackageListingResponse {

    private Long spaId;
    private String spaName;
    private List<SpaPackageResponse> packages;

    public SpaPackageListingResponse() {
    }

    public SpaPackageListingResponse(Long spaId, String spaName, List<SpaPackageResponse> packages) {
        this.spaId = spaId;
        this.spaName = spaName;
        this.packages = packages;
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

    public List<SpaPackageResponse> getPackages() {
        return packages;
    }

    public void setPackages(List<SpaPackageResponse> packages) {
        this.packages = packages;
    }
}
