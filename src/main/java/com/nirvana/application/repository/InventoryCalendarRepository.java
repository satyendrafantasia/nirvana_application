package com.nirvana.application.repository;

import com.nirvana.application.model.InventoryCalendarEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryCalendarRepository extends JpaRepository<InventoryCalendarEntry, Long> {
    List<InventoryCalendarEntry> findBySpa_IdAndServiceDateBetween(Long spaId, LocalDate start, LocalDate end);
    List<InventoryCalendarEntry> findBySpa_IdAndService_IdAndServiceDateBetween(Long spaId, Long serviceId, LocalDate start, LocalDate end);
    Optional<InventoryCalendarEntry> findBySpa_IdAndService_IdAndServiceDate(Long spaId, Long serviceId, LocalDate date);
    Optional<InventoryCalendarEntry> findBySpa_IdAndServiceDate(Long spaId, LocalDate date);
}
