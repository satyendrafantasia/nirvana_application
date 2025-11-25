package com.nirvana.application.repository;

import com.nirvana.application.model.BookingHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingHoldRepository extends JpaRepository<BookingHold, Long> {

    @Query("""
            SELECT COALESCE(SUM(bh.holdUnits), 0) FROM BookingHold bh
            WHERE bh.slot.id = :slotId
              AND bh.expiresAt > :now
              AND bh.convertedToBooking = false
            """)
    Integer findActiveHoldUnits(@Param("slotId") Long slotId, @Param("now") OffsetDateTime now);

    @Query("""
            SELECT bh.slot.id, COALESCE(SUM(bh.holdUnits), 0) FROM BookingHold bh
            WHERE bh.slot.id IN :slotIds
              AND bh.expiresAt > :now
              AND bh.convertedToBooking = false
            GROUP BY bh.slot.id
            """)
    List<Object[]> findActiveHoldUnitsForSlots(@Param("slotIds") Collection<Long> slotIds,
                                               @Param("now") OffsetDateTime now);

    void deleteByExpiresAtBefore(OffsetDateTime cutoff);

    Optional<BookingHold> findByHoldToken(String holdToken);
}
