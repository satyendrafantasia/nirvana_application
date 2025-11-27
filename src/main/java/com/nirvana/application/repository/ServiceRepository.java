package com.nirvana.application.repository;

// src/main/java/com/nirvana/application/repository/ServiceRepository.java


import com.nirvana.application.model.Service;
import com.nirvana.application.repository.projection.SpaStartingPriceProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ServiceRepository extends JpaRepository<Service, Long> {

    List<Service> findBySpaIdAndIsActiveTrueOrderByNameAsc(Long spaId);

    @Query("""
            SELECT s.spa.id AS spaId,
                   MIN(COALESCE(s.priceCents, s.basePriceCents)) AS startingPriceCents
            FROM Service s
            WHERE s.spa.id IN :spaIds
              AND s.isActive = true
              AND s.isVisibleOnMarketplace = true
            GROUP BY s.spa.id
            """)
    List<SpaStartingPriceProjection> findStartingPricesBySpaIds(@Param("spaIds") Collection<Long> spaIds);
}
