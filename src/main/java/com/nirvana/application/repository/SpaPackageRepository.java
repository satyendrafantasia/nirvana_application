package com.nirvana.application.repository;

import com.nirvana.application.model.spa.SpaPackage;
import com.nirvana.application.model.enums.spa.SpaPackageLevel;
import com.nirvana.application.model.enums.spa.SpaPackageStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpaPackageRepository extends JpaRepository<SpaPackage, Long> {

    List<SpaPackage> findBySpaId(Long spaId);

    List<SpaPackage> findBySpaIdAndStatus(Long spaId, SpaPackageStatus status);

    Optional<SpaPackage> findBySpaIdAndId(Long spaId, Long packageId);

    Optional<SpaPackage> findBySpaIdAndIdAndStatus(Long spaId, Long packageId, SpaPackageStatus status);

    Optional<SpaPackage> findBySpaIdAndLevel(Long spaId, SpaPackageLevel level);
}
