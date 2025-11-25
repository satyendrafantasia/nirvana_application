package com.nirvana.application.repository;

import com.nirvana.application.model.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    @Query("""
            SELECT w FROM WaitlistEntry w
            WHERE w.slot.id = :slotId
              AND w.active = true
              AND w.notified = false
            ORDER BY w.createdAt ASC
            """)
    List<WaitlistEntry> findActiveForSlot(@Param("slotId") Long slotId);

    Optional<WaitlistEntry> findBySlotIdAndUserId(Long slotId, Long userId);
}
