package com.nirvana.application.service;

import com.nirvana.application.model.dto.CreateOrUpdateSpaPackageRequest;
import com.nirvana.application.model.dto.SpaPackageResponse;

import java.util.List;

public interface SpaPackageService {

    SpaPackageResponse createOrUpdateSpaPackage(Long spaOwnerId, Long spaId, CreateOrUpdateSpaPackageRequest request);

    List<SpaPackageResponse> getSpaPackages(Long spaId);

    List<SpaPackageResponse> getSpaPackagesForOwner(Long spaOwnerId, Long spaId);
}
