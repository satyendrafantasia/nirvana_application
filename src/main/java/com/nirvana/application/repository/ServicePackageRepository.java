package com.nirvana.application.repository;

import com.nirvana.application.model.ServicePackage;
import com.nirvana.application.model.enums.PackageType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServicePackageRepository extends JpaRepository<ServicePackage, Long> {

    @EntityGraph(attributePaths = "eligibleSpas")
    Optional<ServicePackage> findByPackageTypeAndActiveTrue(PackageType packageType);
}
