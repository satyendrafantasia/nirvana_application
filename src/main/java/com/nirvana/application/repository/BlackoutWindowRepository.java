package com.nirvana.application.repository;

import com.nirvana.application.model.BlackoutWindow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface BlackoutWindowRepository extends JpaRepository<BlackoutWindow, Long> {
    List<BlackoutWindow> findBySpa_IdAndStartTsLessThanEqualAndEndTsGreaterThanEqual(Long spaId, OffsetDateTime start, OffsetDateTime end);
    List<BlackoutWindow> findByProvider_IdAndStartTsLessThanEqualAndEndTsGreaterThanEqual(Long providerId, OffsetDateTime start, OffsetDateTime end);
    List<BlackoutWindow> findBySpa_Id(Long spaId);
}
