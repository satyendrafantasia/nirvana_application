package com.nirvana.application.repository;

import com.nirvana.application.model.ServicePackage;
import com.nirvana.application.model.enums.PackageType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServicePackageRepository extends JpaRepository<ServicePackage, Long> {

    @EntityGraph(attributePaths = "eligibleSpas")
    Optional<ServicePackage> findByPackageTypeAndActiveTrue(PackageType packageType);

    /** Whether a package can be used at a spa, without loading the package's 3,000+ spas. */
    @org.springframework.data.jpa.repository.Query(value = "SELECT COUNT(*) FROM service_package_spa WHERE package_id = :packageId AND spa_id = :spaId", nativeQuery = true)
    long countEligible(@org.springframework.data.repository.query.Param("packageId") Long packageId,
                       @org.springframework.data.repository.query.Param("spaId") Long spaId);
}
