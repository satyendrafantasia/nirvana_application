// File: `src/main/java/com/nirvana/application/repository/SpaRepository.java`
package com.nirvana.application.repository;

import com.nirvana.application.model.Spa;
import com.nirvana.application.repository.projection.SpaDistanceProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpaRepository extends JpaRepository<Spa, Long>, JpaSpecificationExecutor<Spa> {

    @Query(value = """
            SELECT
                s.id AS id,
                s.name AS name,
                (
                    6371 * acos(
                        cos(radians(:lat)) * cos(radians(s.latitude)) *
                        cos(radians(s.longitude) - radians(:lon)) +
                        sin(radians(:lat)) * sin(radians(s.latitude))
                    )
                ) AS distanceKm
            FROM spa s
            WHERE s.latitude IS NOT NULL
              AND s.longitude IS NOT NULL
              AND (
                    6371 * acos(
                        cos(radians(:lat)) * cos(radians(s.latitude)) *
                        cos(radians(s.longitude) - radians(:lon)) +
                        sin(radians(:lat)) * sin(radians(s.latitude))
                    )
              ) <= :radiusKm
            ORDER BY distanceKm
            """,
            nativeQuery = true)
    List<SpaDistanceProjection> findSpasWithinRadiusKm(@Param("lat") double lat,
                                                       @Param("lon") double lon,
                                                       @Param("radiusKm") double radiusKm);

    @Query(value = """
        SELECT
            s.id AS id,
            s.name AS name,
            (
                6371 * acos(
                    cos(radians(:lat)) * cos(radians(s.latitude)) *
                    cos(radians(s.longitude) - radians(:lon)) +
                    sin(radians(:lat)) * sin(radians(s.latitude))
                )
            ) AS distanceKm
        FROM spa s
        WHERE s.latitude BETWEEN :minLat AND :maxLat
          AND s.longitude BETWEEN :minLon AND :maxLon
          AND (
                6371 * acos(
                    cos(radians(:lat)) * cos(radians(s.latitude)) *
                    cos(radians(s.longitude) - radians(:lon)) +
                    sin(radians(:lat)) * sin(radians(s.latitude))
                )
          ) <= :radiusKm
        ORDER BY distanceKm
        """,
            nativeQuery = true)
    List<SpaDistanceProjection> findSpasWithinRadiusKmAndBox(@Param("lat") double lat,
                                                             @Param("lon") double lon,
                                                             @Param("radiusKm") double radiusKm,
                                                             @Param("minLat") double minLat,
                                                             @Param("maxLat") double maxLat,
                                                             @Param("minLon") double minLon,
                                                             @Param("maxLon") double maxLon);

    @Query(value = """
        SELECT
            s.id AS id,
            s.name AS name,
            (
                6371 * acos(
                    cos(radians(:lat)) * cos(radians(s.latitude)) *
                    cos(radians(s.longitude) - radians(:lon)) +
                    sin(radians(:lat)) * sin(radians(s.latitude))
                )
            ) AS distanceKm
        FROM spa s
        WHERE s.latitude BETWEEN :minLat AND :maxLat
          AND s.longitude BETWEEN :minLon AND :maxLon
          AND (
                6371 * acos(
                    cos(radians(:lat)) * cos(radians(s.latitude)) *
                    cos(radians(s.longitude) - radians(:lon)) +
                    sin(radians(:lat)) * sin(radians(s.latitude))
                )
          ) <= :radiusKm
        ORDER BY distanceKm
        """,
            countQuery = """
        SELECT count(*) FROM spa s
        WHERE s.latitude BETWEEN :minLat AND :maxLat
          AND s.longitude BETWEEN :minLon AND :maxLon
          AND (
                6371 * acos(
                    cos(radians(:lat)) * cos(radians(s.latitude)) *
                    cos(radians(s.longitude) - radians(:lon)) +
                    sin(radians(:lat)) * sin(radians(s.latitude))
                )
          ) <= :radiusKm
        """,
            nativeQuery = true)
    Page<SpaDistanceProjection> findSpasWithinRadiusKmAndBoxPaged(
            @Param("lat") double lat,
            @Param("lon") double lon,
            @Param("radiusKm") double radiusKm,
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLon") double minLon,
            @Param("maxLon") double maxLon,
            org.springframework.data.domain.Pageable pageable
    );

    // Only spas belonging to the logged-in spa owner (via SpaManager -> AppUser.id)
    Optional<Spa> findByIdAndSpaManager_User_Id(Long id, Long userId);

    List<Spa> findBySpaManager_User_Id(Long userId);

    Optional<Spa> findByAddress_GooglePlaceId(String googlePlaceId);
}
