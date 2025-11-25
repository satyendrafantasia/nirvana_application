package com.nirvana.application.repository;

import com.nirvana.application.model.Slot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    /**
     * Fetch available slots for a single day (used by simple “date” view).
     *
     * Conditions:
     *  - belongs to spa + service
     *  - starts within [start, end)
     *  - not blocked
     *  - status = OPEN
     *  - capacity not fully booked
     */
    @Query("""
        SELECT s FROM Slot s
        WHERE s.spa.id = :spaId
          AND s.service.id = :serviceId
          AND s.startTs >= :start
          AND s.startTs < :end
          AND s.isBlocked = false
          AND s.status = com.nirvana.application.model.enums.SlotStatus.OPEN
          AND s.bookedUnits < s.capacityUnit
        ORDER BY s.startTs ASC
        """)
    List<Slot> findAvailableSlotsForServiceOnDay(
            @Param("spaId") Long spaId,
            @Param("serviceId") Long serviceId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    /**
     * Fetch all slots in a time range for a spa+service (used by week-range / calendar view).
     *
     * Filtering by:
     *  - spa
     *  - service
     *  - [startTs, endTs] window
     *  - only OPEN + not blocked
     *
     * Capacity checks (guests vs bookedUnits) are done in service layer
     * (SlotAvailabilityService.isSlotBookable).
     */
    @Query("""
        SELECT s FROM Slot s
        WHERE s.spa.id = :spaId
          AND s.service.id = :serviceId
          AND s.startTs >= :startTs
          AND s.endTs <= :endTs
          AND s.isBlocked = false
          AND s.status = com.nirvana.application.model.enums.SlotStatus.OPEN
          AND s.bookedUnits < s.capacityUnit
        ORDER BY s.startTs ASC
        """)
    List<Slot> findSlotsForServiceBetween(
            @Param("spaId") Long spaId,
            @Param("serviceId") Long serviceId,
            @Param("startTs") OffsetDateTime startTs,
            @Param("endTs") OffsetDateTime endTs
    );

    /**
     * Pessimistic lock for booking confirmation / overbooking protection.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Slot s WHERE s.id = :id")
    Optional<Slot> findByIdForUpdate(@Param("id") Long id);
}
