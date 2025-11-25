package com.nirvana.application.repository;

import com.nirvana.application.model.ProviderAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProviderAuditLogRepository extends JpaRepository<ProviderAuditLog, Long> {
    List<ProviderAuditLog> findBySpa_IdOrderByCreatedAtDesc(Long spaId);
}
