package com.nirvana.application.repository;

import com.nirvana.application.model.SupportOverride;
import com.nirvana.application.model.enums.SupportOverrideStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportOverrideRepository extends JpaRepository<SupportOverride, Long> {
    List<SupportOverride> findBySpa_Id(Long spaId);
    List<SupportOverride> findByBooking_Id(Long bookingId);
    long countBySpa_IdAndStatus(Long spaId, SupportOverrideStatus status);
}
