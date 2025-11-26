package com.nirvana.application.repository;

import com.nirvana.application.model.Therapist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TherapistRepository extends JpaRepository<Therapist, Long> {

    @Query("""
            SELECT DISTINCT t FROM Therapist t
            LEFT JOIN t.services svc
            WHERE t.spa.id = :spaId
              AND t.isActive = true
              AND t.isAvailable = true
              AND (t.service.id = :serviceId OR svc.id = :serviceId)
            """)
    List<Therapist> findActiveAvailableForSpaAndService(@Param("spaId") Long spaId, @Param("serviceId") Long serviceId);
}
