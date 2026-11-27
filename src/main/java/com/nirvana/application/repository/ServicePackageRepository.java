package com.nirvana.application.repository;

import com.nirvana.application.model.ServicePackage;
import com.nirvana.application.model.enums.PackageType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServicePackageRepository extends JpaRepository<ServicePackage, Long> {

    @EntityGraph(attributePaths = "eligibleSpas")
    Optional<ServicePackage> findByPackageTypeAndActiveTrue(PackageType packageType);

    String REDEEMABLE_SPA = "s.is_active=1 AND s.is_verified=1 AND s.booking_mode='INSTANT'";
    String ELIGIBLE_COUNT = "SELECT COUNT(*) FROM service_package_spa ps JOIN spa s ON s.id=ps.spa_id "
            + "WHERE ps.package_id=:packageId AND " + REDEEMABLE_SPA;

    @org.springframework.data.jpa.repository.Query(value = ELIGIBLE_COUNT, nativeQuery = true)
    long countRedeemableSpas(@org.springframework.data.repository.query.Param("packageId") Long packageId);

    /** Whether a package can be used at a spa, without loading the package's 3,000+ spas. */
    @org.springframework.data.jpa.repository.Query(value = ELIGIBLE_COUNT + " AND ps.spa_id=:spaId", nativeQuery = true)
    long countEligible(@org.springframework.data.repository.query.Param("packageId") Long packageId,
                       @org.springframework.data.repository.query.Param("spaId") Long spaId);
}
