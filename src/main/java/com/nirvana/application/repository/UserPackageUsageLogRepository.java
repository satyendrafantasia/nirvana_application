package com.nirvana.application.repository;

import com.nirvana.application.model.UserPackageUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserPackageUsageLogRepository extends JpaRepository<UserPackageUsageLog, Long> {

    List<UserPackageUsageLog> findByUserIdOrderByUsedAtDesc(Long userId);
}
