package com.nirvana.application.repository;

import com.nirvana.application.model.Spa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SpaRepository extends JpaRepository<Spa, Long> {

    Optional<Spa> findByName(String name);

    List<Spa> findAllBySpaManager_Id(Long id);

    Optional<Spa> findByIdAndSpaManager_Id(Long id, Long managerId);

    @Query("SELECT h FROM Spa h WHERE h.address.city = :city")
    List<Spa> findSpasByCity(@Param("city") String city);

    @Query("SELECT h " +
            "FROM Spa h " +
            "JOIN h.rooms r " +
            "LEFT JOIN Availability a ON a.room.id = r.id " +
            "AND a.date >= :checkinDate AND a.date < :checkoutDate " +
            "WHERE h.address.city = :city " +
            "AND (a IS NULL OR a.availableRooms > 0) " +
            "GROUP BY h.id, r.id " +
            "HAVING COUNT(DISTINCT a.date) + SUM(CASE WHEN a IS NULL THEN 1 ELSE 0 END) = :numberOfDays")
    List<Spa> findSpasWithAvailableRooms(@Param("city") String city,
                                             @Param("checkinDate") LocalDate checkinDate,
                                             @Param("checkoutDate") LocalDate checkoutDate,
                                             @Param("numberOfDays") Long numberOfDays);

    @Query("SELECT h " +
            "FROM Spa h " +
            "WHERE h.address.city = :city " +
            "AND NOT EXISTS (" +
            "   SELECT 1 " +
            "   FROM Availability a " +
            "   WHERE a.room.Spa.id = h.id " +
            "   AND a.date >= :checkinDate AND a.date < :checkoutDate" +
            ")")
    List<Spa> findSpasWithoutAvailabilityRecords(@Param("city") String city,
                                                     @Param("checkinDate") LocalDate checkinDate,
                                                     @Param("checkoutDate") LocalDate checkoutDate);

    @Query("SELECT h " +
            "FROM Spa h " +
            "JOIN h.rooms r " +
            "LEFT JOIN Availability a ON r.id = a.room.id " +
            "AND a.date >= :checkinDate AND a.date < :checkoutDate " +
            "WHERE h.address.city = :city " +
            "AND (a IS NULL OR a.availableRooms > 0) " +
            "GROUP BY h.id " +
            "HAVING COUNT(DISTINCT a.date) < :numberOfDays " +
            "AND COUNT(DISTINCT CASE WHEN a.availableRooms > 0 THEN a.date END) > 0")
    List<Spa> findSpasWithPartialAvailabilityRecords(@Param("city") String city,
                                                         @Param("checkinDate") LocalDate checkinDate,
                                                         @Param("checkoutDate") LocalDate checkoutDate,
                                                         @Param("numberOfDays") Long numberOfDays);

}
