package com.nirvana.application.repository;

import com.nirvana.application.model.AdminActionLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminActionLogRepository extends JpaRepository<AdminActionLog, Long> {

    List<AdminActionLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
